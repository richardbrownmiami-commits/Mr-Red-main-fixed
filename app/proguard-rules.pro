# Keep NARS beliefs serializable
-keep class com.aibot.Belief { *; }
-keep class com.aibot.NeuralNetwork { *; }
-keep class com.aibot.Tokenizer { *; }

# Jsoup
-keep public class org.jsoup.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }

# Keep serialization
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
