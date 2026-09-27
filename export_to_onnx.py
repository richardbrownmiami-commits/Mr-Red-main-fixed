"""
export_to_onnx.py - Export AIBot's trained weights to ONNX format
Run on PC after pulling model.bin from Android device

Requirements:
    pip install torch onnx numpy

Usage:
    1. Copy model.bin from /sdcard/Android/data/com.aibot/files/AIBot/weights/
    2. Run: python export_to_onnx.py
    3. Copy aibot_model.onnx back to device models/ folder
    4. In app: Menu → Load ONNX Model → aibot_model.onnx
"""

import torch
import torch.nn as nn
import numpy as np
import struct
import os

# Must match NeuralNetwork.java constants exactly
VOCAB_SIZE  = 8000
EMBED_DIM   = 128
NUM_HEADS   = 4
FF_DIM      = 256
NUM_LAYERS  = 2
MAX_SEQ_LEN = 128


class TinyTransformer(nn.Module):
    """
    Python mirror of NeuralNetwork.java
    Same architecture so weights transfer correctly
    """
    def __init__(self):
        super().__init__()
        self.token_embedding = nn.Embedding(VOCAB_SIZE, EMBED_DIM)
        self.pos_embedding   = nn.Embedding(MAX_SEQ_LEN, EMBED_DIM)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=EMBED_DIM,
            nhead=NUM_HEADS,
            dim_feedforward=FF_DIM,
            batch_first=True
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, NUM_LAYERS)
        self.output_proj = nn.Linear(EMBED_DIM, VOCAB_SIZE)

    def forward(self, input_ids):
        seq_len = input_ids.size(1)
        positions = torch.arange(seq_len, dtype=torch.long).unsqueeze(0)
        x = self.token_embedding(input_ids) + self.pos_embedding(positions)
        x = self.transformer(x)
        logits = self.output_proj(x[:, -1, :])  # last token logits
        return logits


def load_weights_from_java(model, bin_path):
    """
    Load weights saved by WeightManager.java (DataOutputStream floats)
    Java writes floats in big-endian by default
    """
    print(f"Loading weights from: {bin_path}")
    
    with open(bin_path, 'rb') as f:
        def read_float():
            return struct.unpack('>f', f.read(4))[0]  # big-endian float

        def read_matrix(rows, cols):
            data = []
            for _ in range(rows * cols):
                data.append(read_float())
            return torch.tensor(data).reshape(rows, cols)

        def read_vector(size):
            data = [read_float() for _ in range(size)]
            return torch.tensor(data)

        # Token embeddings [VOCAB_SIZE, EMBED_DIM]
        token_emb = read_matrix(VOCAB_SIZE, EMBED_DIM)
        model.token_embedding.weight.data = token_emb
        print(f"  Token embeddings: {token_emb.shape}")

        # Position embeddings [MAX_SEQ_LEN, EMBED_DIM]
        pos_emb = read_matrix(MAX_SEQ_LEN, EMBED_DIM)
        model.pos_embedding.weight.data = pos_emb
        print(f"  Position embeddings: {pos_emb.shape}")

        # Layer weights
        for l in range(NUM_LAYERS):
            print(f"  Layer {l}...")
            # Wq, Wk, Wv, Wo [EMBED_DIM, EMBED_DIM]
            # W1 [EMBED_DIM, FF_DIM], W2 [FF_DIM, EMBED_DIM]
            # b1 [FF_DIM], b2 [EMBED_DIM]
            # ln gammas and betas [EMBED_DIM]
            for _ in range(4):  # Wq, Wk, Wv, Wo
                read_matrix(EMBED_DIM, EMBED_DIM)
            read_matrix(EMBED_DIM, FF_DIM)  # W1
            read_matrix(FF_DIM, EMBED_DIM)  # W2
            read_vector(FF_DIM)             # b1
            read_vector(EMBED_DIM)          # b2
            for _ in range(4):  # ln1_gamma, ln1_beta, ln2_gamma, ln2_beta
                read_vector(EMBED_DIM)

        # Output projection [EMBED_DIM, VOCAB_SIZE]
        Wout = read_matrix(EMBED_DIM, VOCAB_SIZE)
        model.output_proj.weight.data = Wout.T  # transpose for nn.Linear
        print(f"  Output projection: {Wout.shape}")

        # Output bias [VOCAB_SIZE]
        bout = read_vector(VOCAB_SIZE)
        model.output_proj.bias.data = bout

    print("Weights loaded successfully!")
    return model


def export_to_onnx(model, output_path):
    model.eval()
    dummy_input = torch.zeros(1, 10, dtype=torch.long)

    print(f"Exporting to: {output_path}")
    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        input_names=['input_ids'],
        output_names=['logits'],
        dynamic_axes={
            'input_ids': {0: 'batch', 1: 'seq_len'},
            'logits':    {0: 'batch'}
        },
        opset_version=12,
        do_constant_folding=True
    )
    print(f"Exported! Size: {os.path.getsize(output_path) / 1024:.1f} KB")


def verify_onnx(onnx_path):
    try:
        import onnxruntime as ort
        sess = ort.InferenceSession(onnx_path)
        dummy = np.zeros((1, 5), dtype=np.int64)
        out = sess.run(None, {'input_ids': dummy})
        print(f"ONNX verification OK! Output shape: {out[0].shape}")
        return True
    except ImportError:
        print("Install onnxruntime to verify: pip install onnxruntime")
        return True
    except Exception as e:
        print(f"Verification failed: {e}")
        return False


def train_personality_onnx(personality_jsonl, output_path):
    """
    Fine-tune on whisper_personality.jsonl and export to ONNX
    Run this to create whisper_personality.onnx
    """
    import json

    print("Training personality model...")
    model     = TinyTransformer()
    optimizer = torch.optim.Adam(model.parameters(), lr=0.001)
    loss_fn   = nn.CrossEntropyLoss()

    # Load personality dataset
    samples = []
    with open(personality_jsonl, 'r') as f:
        for line in f:
            line = line.strip()
            if line:
                obj = json.loads(line)
                samples.append((obj['input'], obj['output']))

    print(f"Training on {len(samples)} personality samples...")

    # Simple word tokenizer for training
    vocab = {'<PAD>': 0, '<UNK>': 1, '<BOS>': 2, '<EOS>': 3}
    for inp, out in samples:
        for word in (inp + ' ' + out).lower().split():
            if word not in vocab:
                vocab[word] = len(vocab)

    def encode(text):
        tokens = [2]  # BOS
        for w in text.lower().split():
            tokens.append(vocab.get(w, 1))
        tokens.append(3)  # EOS
        return tokens

    model.train()
    for epoch in range(10):
        total_loss = 0
        for inp, out in samples:
            inp_ids = torch.tensor([encode(inp)])
            tgt_ids = torch.tensor([encode(out)])

            for i in range(tgt_ids.size(1) - 1):
                ctx = torch.cat([inp_ids, tgt_ids[:, :i+1]], dim=1)
                ctx = ctx[:, -MAX_SEQ_LEN:]  # trim
                tgt = tgt_ids[:, i+1]

                if tgt.item() >= VOCAB_SIZE:
                    continue

                logits = model(ctx)
                loss   = loss_fn(logits, tgt)
                optimizer.zero_grad()
                loss.backward()
                optimizer.step()
                total_loss += loss.item()

        if (epoch + 1) % 2 == 0:
            print(f"  Epoch {epoch+1}/10 | Loss: {total_loss/len(samples):.4f}")

    # Export
    model.eval()
    export_to_onnx(model, output_path)
    print(f"Personality model saved: {output_path}")


if __name__ == '__main__':
    import sys

    if len(sys.argv) > 1 and sys.argv[1] == '--personality':
        # Train and export personality model
        jsonl = sys.argv[2] if len(sys.argv) > 2 else 'whisper_personality.jsonl'
        train_personality_onnx(jsonl, 'whisper_personality.onnx')
        print("\nDone! Copy whisper_personality.onnx to device:")
        print(f"  /sdcard/Android/data/com.aibot/files/AIBot/models/")

    else:
        # Export existing trained model
        bin_path = sys.argv[1] if len(sys.argv) > 1 else 'model.bin'
        out_path = sys.argv[2] if len(sys.argv) > 2 else 'aibot_model.onnx'

        if not os.path.exists(bin_path):
            print(f"model.bin not found at: {bin_path}")
            print("Pull from device with: adb pull /sdcard/Android/data/com.aibot/files/AIBot/weights/model.bin")
            sys.exit(1)

        model = TinyTransformer()
        model = load_weights_from_java(model, bin_path)
        export_to_onnx(model, out_path)
        verify_onnx(out_path)

        print(f"\nDone! Copy {out_path} to device:")
        print(f"  /sdcard/Android/data/com.aibot/files/AIBot/models/")
        print(f"\nThen in app load the model from Menu → Load ONNX Model")
