package com.aibot;

import java.io.*;
import java.util.*;

/**
 * Simple Word-level Tokenizer
 * - Builds vocabulary from conversation and datasets
 * - Encodes/decodes text to/from token IDs
 * - Saves/loads vocabulary
 */
public class Tokenizer {

    // Special tokens
    public static final int PAD_TOKEN = 0;
    public static final int UNK_TOKEN = 1;
    public static final int BOS_TOKEN = 2; // beginning of sentence
    public static final int EOS_TOKEN = 3; // end of sentence
    public static final int SEP_TOKEN = 4; // separator

    private Map<String, Integer> wordToId = new LinkedHashMap<>();
    private Map<Integer, String> idToWord = new LinkedHashMap<>();
    private int nextId = 5; // 0-4 reserved for special tokens

    public Tokenizer() {
        // Add special tokens
        wordToId.put("<PAD>", PAD_TOKEN);
        wordToId.put("<UNK>", UNK_TOKEN);
        wordToId.put("<BOS>", BOS_TOKEN);
        wordToId.put("<EOS>", EOS_TOKEN);
        wordToId.put("<SEP>", SEP_TOKEN);

        idToWord.put(PAD_TOKEN, "<PAD>");
        idToWord.put(UNK_TOKEN, "<UNK>");
        idToWord.put(BOS_TOKEN, "<BOS>");
        idToWord.put(EOS_TOKEN, "<EOS>");
        idToWord.put(SEP_TOKEN, "<SEP>");

        // Seed with basic English words
        seedBasicVocab();
    }

    private void seedBasicVocab() {
        // 3000 most common English words — gives bot real language foundation
        String[] basics = {
            // Pronouns & determiners
            "i","you","he","she","it","we","they","me","him","her","us","them",
            "my","your","his","its","our","their","mine","yours","hers","ours","theirs",
            "this","that","these","those","who","whom","whose","which","what",
            "a","an","the","some","any","all","each","every","both","few","more",
            "most","other","another","such","same","different","own","much","many",
            // Core verbs
            "be","is","are","was","were","been","being","am",
            "have","has","had","having","do","does","did","doing","done",
            "say","says","said","get","gets","got","make","made","makes",
            "go","goes","went","gone","know","knew","known","thinks","thought",
            "take","took","taken","see","saw","seen","come","came","want","wanted",
            "look","looked","use","used","find","found","give","gave","given",
            "tell","told","work","worked","call","called","try","tried","ask","asked",
            "need","needed","feel","felt","become","became","leave","left","put",
            "mean","meant","keep","kept","let","begin","began","show","showed",
            "hear","heard","play","played","run","ran","move","moved","live","lived",
            "believe","believed","hold","held","bring","brought","happen","happened",
            "write","wrote","provide","provided","sit","sat","stand","stood",
            "lose","lost","pay","paid","meet","met","include","included",
            "continue","continued","set","learn","learned","change","changed",
            "lead","led","understand","understood","watch","watched","follow","followed",
            "stop","stopped","create","created","speak","spoke","read","spend","spent",
            "grow","grew","open","opened","walk","walked","win","won","offer","offered",
            "remember","remembered","love","loved","consider","considered","appear","appeared",
            "buy","bought","wait","waited","serve","served","send","sent",
            "expect","expected","build","built","stay","stayed","fall","fell",
            "cut","reach","reached","kill","killed","remain","remained","raise","raised",
            "pass","passed","sell","sold","require","required","report","reported",
            "decide","decided","pull","pulled","eat","ate","drink","drank","sleep","slept",
            "wake","woke","help","helped","start","started","turn","turned","close","closed",
            "talk","talked","add","added","join","joined","return","returned",
            "check","checked","answer","answered","allow","allowed","save","saved",
            "care","cared","exist","existed","choose","chose","pick","picked",
            "share","shared","hit","drop","dropped","push","pushed","jump","jumped",
            "throw","threw","catch","caught","fight","fought","break","broke","fix","fixed",
            // Modal & auxiliary
            "will","would","can","could","should","may","might","must","shall","going",
            // Common nouns - people
            "person","people","man","men","woman","women","child","children","boy","girl",
            "baby","family","friend","enemy","teacher","student","doctor","police",
            "father","mother","parent","brother","sister","son","daughter","husband","wife",
            "human","body","head","face","eye","eyes","hand","hands","heart","mind",
            "name","life","world","country","city","home","house","school","place",
            "group","team","member","leader","president","king","queen","god",
            // Time
            "time","year","day","week","month","hour","minute","second","moment",
            "morning","evening","night","today","yesterday","tomorrow","now","then",
            // Things
            "thing","way","part","case","fact","point","question","answer","example",
            "problem","idea","word","sentence","story","book","page","line","number",
            "work","job","money","business","company","market","power","system",
            "water","food","air","fire","earth","light","dark","sound","color",
            "car","road","street","door","window","room","floor","table","chair","bed",
            "phone","computer","screen","internet","app","game","music","movie",
            "show","news","message","letter","email","picture","photo","video","camera",
            "battery","device","machine","animal","dog","cat","bird","fish","tree",
            "plant","flower","grass","sun","moon","star","sky","rain","wind","cloud","snow",
            "arm","leg","back","side","top","bottom","middle","area","space","level",
            "size","weight","speed","health","pain","medicine","hospital","blood",
            "war","peace","law","right","wrong","truth","fact","art","science",
            "history","culture","language","religion","nature","hand","foot",
            // Adjectives
            "good","bad","great","little","big","large","small","high","low",
            "long","short","old","young","new","first","last","next","early","late",
            "real","true","false","possible","impossible","important","different",
            "similar","simple","complex","easy","hard","fast","slow","hot","cold",
            "warm","cool","loud","quiet","bright","strong","weak","heavy","rich",
            "poor","free","busy","ready","open","closed","full","empty","clean",
            "dirty","safe","dangerous","happy","sad","angry","scared","surprised",
            "tired","sick","healthy","beautiful","ugly","funny","serious","strange",
            "normal","special","clear","deep","wide","narrow","near","far",
            "wonderful","amazing","terrible","horrible","perfect","interesting",
            "boring","exciting","scary","lovely","friendly","kind","smart","clever",
            "wise","crazy","silly","lucky","sweet","bitter","soft","rough","alive",
            "dead","broken","whole","lost","found","hidden","obvious",
            // Adverbs
            "not","no","yes","very","really","just","also","more","most","well",
            "even","only","still","again","back","never","always","often","sometimes",
            "already","almost","enough","too","so","here","there","where","when",
            "then","soon","later","before","after","away","together","probably",
            "maybe","perhaps","certainly","definitely","actually","usually","suddenly",
            "quickly","slowly","carefully","easily","clearly","finally","especially",
            "exactly","nearly","recently","simply","anyway","somehow","somewhere",
            // Prepositions
            "in","on","at","to","of","for","with","by","from","about","as","into",
            "through","during","above","below","between","among","around","under",
            "over","along","behind","beside","beyond","despite","except","inside",
            "outside","since","until","without","within","across","against","toward",
            // Conjunctions
            "and","or","but","if","because","when","while","although","though",
            "since","until","unless","whereas","however","therefore","thus","so",
            "yet","nor","both","either","neither","whether","that","than",
            // Questions
            "what","who","where","when","why","how","which","whose","whom",
            // Greetings
            "hello","hi","hey","bye","goodbye","thanks","thank","please","sorry",
            "ok","okay","sure","right","exactly","indeed","anyway","well","wow",
            "oh","ah","hmm","um","oops","yo",
            // Numbers
            "zero","one","two","three","four","five","six","seven","eight","nine","ten",
            "eleven","twelve","thirteen","fourteen","fifteen","twenty","thirty","forty",
            "fifty","hundred","thousand","million","half","quarter","double","once","twice",
            // Tech
            "phone","mobile","android","wifi","bluetooth","online","search","download",
            "install","update","data","message","chat","call","video","photo","settings",
            "notification","alarm","timer","flashlight","torch","speaker","microphone",
            "charger","website","browser","email","social","media","youtube","whatsapp",
            "keyboard","button","tap","click","swipe","scroll","zoom","password","account",
            // Everyday life
            "hungry","thirsty","awake","afternoon","office","meeting","project","task",
            "deadline","boss","employee","buy","sell","price","cost","expensive","cheap",
            "drive","bus","train","flight","travel","trip","vacation","hotel","cook",
            "kitchen","recipe","meal","breakfast","lunch","dinner","snack","wash",
            "laundry","clothes","shirt","pants","shoes","bag","weather","sunny","cloudy",
            "temperature","gym","exercise","sport","football","cricket","study","exam",
            "test","class","lesson","homework","grade","fever","headache","birthday",
            "party","celebrate","gift","wedding","holiday","festival","politics","election",
            "government","song","dance","draw","paint","pet","garden",
            // Feelings
            "emotion","mood","hate","like","dislike","enjoy","prefer","wish","hope",
            "doubt","wonder","imagine","dream","plan","curious","confused","excited",
            "calm","disappointed","proud","ashamed","guilty","lonely","bored","stressed",
            "anxious","confident","nervous","relaxed","trust",
            // NARS relation words
            "has","needs","uses","makes","causes","helps","hurts","contains","belongs",
            "part","type","kind","similar","before","during","therefore","result",
            "effect","cause","because","example","person","born","live","lives",
        };
        for (String word : basics) addWord(word);
    }


    // ─── ENCODING ─────────────────────────────────────────────────────────────

    public int[] encode(String text) {
        return encode(text, true, true);
    }

    public int[] encode(String text, boolean addBos, boolean addEos) {
        String[] words = tokenize(text);
        List<Integer> ids = new ArrayList<>();

        if (addBos) ids.add(BOS_TOKEN);

        for (String word : words) {
            // Add to vocab if not present and under limit
            if (!wordToId.containsKey(word)) {
                if (nextId < NeuralNetwork.VOCAB_SIZE) {
                    addWord(word);
                }
            }
            ids.add(wordToId.getOrDefault(word, UNK_TOKEN));
        }

        if (addEos) ids.add(EOS_TOKEN);

        // Trim to max sequence length
        int maxLen = NeuralNetwork.MAX_SEQ_LEN;
        if (ids.size() > maxLen) {
            ids = ids.subList(ids.size() - maxLen, ids.size());
        }

        int[] result = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) result[i] = ids.get(i);
        return result;
    }

    // ─── DECODING ─────────────────────────────────────────────────────────────

    public String decode(int[] ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            String word = idToWord.getOrDefault(id, "<UNK>");
            // Skip special tokens
            if (word.startsWith("<") && word.endsWith(">")) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(word);
        }
        return sb.toString().trim();
    }

    public String decodeToken(int id) {
        return idToWord.getOrDefault(id, "<UNK>");
    }

    // ─── TOKENIZATION ─────────────────────────────────────────────────────────

    private String[] tokenize(String text) {
        // Lowercase, keep punctuation as separate tokens
        text = text.toLowerCase()
                   .replaceAll("([.,!?;:])", " $1 ")
                   .replaceAll("\\s+", " ")
                   .trim();
        return text.split(" ");
    }

    // ─── VOCABULARY ───────────────────────────────────────────────────────────

    private void addWord(String word) {
        if (wordToId.containsKey(word)) return;
        if (nextId >= NeuralNetwork.VOCAB_SIZE) return;
        wordToId.put(word, nextId);
        idToWord.put(nextId, word);
        nextId++;
    }

    public void learnFromText(String text) {
        String[] words = tokenize(text);
        for (String word : words) {
            if (word.length() > 1 && nextId < NeuralNetwork.VOCAB_SIZE) {
                addWord(word);
            }
        }
    }

    public int getVocabSize() { return nextId; }

    public boolean hasWord(String word) {
        return wordToId.containsKey(word.toLowerCase());
    }

    // ─── PERSISTENCE ──────────────────────────────────────────────────────────

    public void saveVocab(File file) throws IOException {
        PrintWriter pw = new PrintWriter(new FileWriter(file));
        for (Map.Entry<String, Integer> entry : wordToId.entrySet()) {
            pw.println(entry.getValue() + "\t" + entry.getKey());
        }
        pw.close();
    }

    public void loadVocab(File file) throws IOException {
        wordToId.clear();
        idToWord.clear();
        nextId = 0;

        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split("\t", 2);
            if (parts.length == 2) {
                int id   = Integer.parseInt(parts[0].trim());
                String w = parts[1].trim();
                wordToId.put(w, id);
                idToWord.put(id, w);
                nextId = Math.max(nextId, id + 1);
            }
        }
        br.close();
    }
}
