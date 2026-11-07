package com.requiredtruth.voice2ai2voice;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.speech.*;
import android.speech.tts.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile HttpURLConnection activeConnection;
    private volatile int generation=0;
    private Future<?> job;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady=false, listening=false, waiting=false, speaking=false, destroyed=false;
    private Button talk, send, replay, settings;
    private TextView status, answer;
    private EditText transcript;
    private SharedPreferences prefs;
    private String apiKey="", finalAnswer="";
    private final int ink=Color.rgb(229,240,244), muted=Color.rgb(161,182,192), mint=Color.rgb(118,225,203);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("server",MODE_PRIVATE);
        boolean keyFailure=false;
        try { apiKey=KeyStoreSecret.decrypt(prefs.getString("key","")); } catch(Exception e) { keyFailure=true; }
        buildUi();
        if(state!=null) { transcript.setText(state.getString("transcript","")); finalAnswer=state.getString("answer",""); answer.setText(finalAnswer); }
        tts=new TextToSpeech(this,result -> runOnUiThread(() -> {
            if(destroyed) return;
            if(result==TextToSpeech.SUCCESS) {
                int language=tts.setLanguage(Locale.getDefault());
                ttsReady=language!=TextToSpeech.LANG_MISSING_DATA && language!=TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANT).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    public void onStart(String id) {}
                    public void onDone(String id) { if(id.endsWith(":last")) finishSpeech(id,false); }
                    public void onError(String id) { finishSpeech(id,true); }
                });
            }
            if(!ttsReady) status.setText("Voice unavailable. Install or enable a text-to-speech engine. Answers still appear below.");
            updateButtons();
        }));
        if(keyFailure) status.setText("Could not unlock the saved API key. Enter it again in Server settings.");
        else if(prefs.getString("url","").isEmpty()) status.setText("Open Server settings to connect your AI.");
    }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private TextView label(String text,int size,int color) {
        TextView v=new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setPadding(0,dp(8),0,dp(8)); return v;
    }
    private Button button(String text) {
        Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(ink); b.setTextSize(16); b.setMinHeight(dp(52)); return b;
    }
    private void buildUi() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(16,28,36));
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(20),dp(24),dp(24)); scroll.addView(root);
        root.setOnApplyWindowInsetsListener((v,insets) -> { v.setPadding(dp(24),dp(20)+insets.getSystemWindowInsetTop(),dp(24),dp(24)+insets.getSystemWindowInsetBottom()); return insets; });
        TextView eyebrow=label("SPEAK. ASK. HEAR.",12,mint); root.addView(eyebrow);
        TextView title=label("Voice → AI → Voice",30,ink); title.setTypeface(null,Typeface.BOLD); root.addView(title);
        root.addView(label("One tap. A fresh conversation every time.",15,muted));
        settings=button("Server settings"); root.addView(settings); settings.setOnClickListener(v -> showSettings());
        status=label("Ready to listen",16,mint); status.setMinHeight(dp(70)); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(status);
        talk=button("●  Tap to speak"); talk.setTextColor(Color.rgb(16,28,36)); talk.setTextSize(21); talk.setTypeface(null,Typeface.BOLD);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(mint); bg.setCornerRadius(dp(28)); talk.setBackground(bg);
        root.addView(talk,new LinearLayout.LayoutParams(-1,dp(104))); talk.setOnClickListener(v -> { if(listening||waiting||speaking) cancel(); else startListening(); });
        root.addView(label("YOUR WORDS",12,muted));
        transcript=new EditText(this); transcript.setTextColor(ink); transcript.setHintTextColor(muted); transcript.setTextSize(17); transcript.setHint("Your speech appears here. You can also type."); transcript.setMinLines(2); transcript.setMaxLines(6); transcript.setGravity(Gravity.TOP); transcript.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); root.addView(transcript);
        send=button("Send text as a new conversation"); root.addView(send); send.setOnClickListener(v -> submit(transcript.getText().toString()));
        root.addView(label("AI RESPONSE",12,muted)); answer=label("Your answer will appear here and be read aloud.",19,ink); answer.setTextIsSelectable(true); root.addView(answer);
        replay=button("Read answer again"); root.addView(replay); replay.setOnClickListener(v -> speak(finalAnswer));
        root.addView(label("Recognition and playback use your Android speech services. Only the recognized text is sent to your configured AI server.",12,muted));
        setContentView(scroll); updateButtons();
    }
    private void updateButtons() {
        if(talk==null) return;
        talk.setText(listening ? "■  Cancel listening" : waiting ? "■  Cancel request" : speaking ? "■  Stop voice" : "●  Tap to speak");
        send.setEnabled(!listening&&!waiting&&!speaking); settings.setEnabled(!listening&&!waiting&&!speaking); replay.setEnabled(ttsReady&&!finalAnswer.isEmpty()&&!listening&&!waiting&&!speaking);
    }
    private void showSettings() {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText url=field(form,"Server base URL",prefs.getString("url",""),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://your-server.example/v1");
        EditText model=field(form,"Model name",prefs.getString("model",""),InputType.TYPE_CLASS_TEXT);
        EditText key=field(form,"API key (optional)",apiKey,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox http=new CheckBox(this); http.setText("Allow HTTP for a trusted local server"); http.setChecked(prefs.getBoolean("http",false)); form.addView(http);
        form.addView(label("Use the exact model ID from your server. HTTP sends text and credentials without encryption.",13,muted));
        ScrollView sc=new ScrollView(this); sc.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connect your AI").setView(sc).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                ChatApi.endpoint(url.getText().toString(),http.isChecked());
                if(model.getText().toString().trim().isEmpty()) throw new Exception("Enter a model name.");
                String newKey=key.getText().toString().trim();
                String encrypted=KeyStoreSecret.encrypt(newKey);
                prefs.edit().putString("url",url.getText().toString().trim()).putString("model",model.getText().toString().trim()).putString("key",encrypted).putBoolean("http",http.isChecked()).apply();
                apiKey=newKey; status.setText("Server saved. Tap to speak."); dialog.dismiss();
            } catch(Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
        })); dialog.show();
    }
    private EditText field(LinearLayout form,String name,String value,int type) {
        form.addView(label(name,14,muted)); EditText field=new EditText(this); field.setText(value); field.setTextColor(ink); field.setInputType(type); field.setSingleLine(true); field.setSaveEnabled(false); form.addView(field); return field;
    }
    private void startListening() {
        if(prefs.getString("url","").isEmpty()) { showSettings(); return; }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10); return; }
        if(!SpeechRecognizer.isRecognitionAvailable(this)) { status.setText("No speech recognition service installed. Enable Android speech recognition or type below."); return; }
        cancelSilently(); final int token=generation;
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b) { if(token==generation) status.setText("Listening… speak now"); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float rms) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() { if(token==generation) status.setText("Recognizing your words…"); }
            public void onPartialResults(Bundle b) { if(token==generation) setTranscript(b); }
            public void onEvent(int t,Bundle b) {}
            public void onError(int error) {
                if(token!=generation) return;
                listening=false; releaseRecognizer();
                String reason;
                switch(error) {
                    case SpeechRecognizer.ERROR_NO_MATCH: case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: reason="No speech recognized. Tap to try again."; break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: reason="Microphone permission is required. Enable it in Android app settings."; break;
                    case SpeechRecognizer.ERROR_NETWORK: case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: reason="Speech recognition network error. Try again or type below."; break;
                    default: reason="Speech recognition failed ("+error+"). Try again or type below.";
                }
                status.setText(reason); updateButtons();
            }
            public void onResults(Bundle b) {
                if(token!=generation) return;
                ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                listening=false; releaseRecognizer();
                if(words==null||words.isEmpty()) { status.setText("No speech recognized. Try again."); updateButtons(); return; }
                transcript.setText(words.get(0)); submit(words.get(0));
            }
            private void setTranscript(Bundle b) { ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(words!=null&&!words.isEmpty()) transcript.setText(words.get(0)); }
        });
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag()).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
        try { listening=true; transcript.setText(""); status.setText("Starting microphone…"); updateButtons(); recognizer.startListening(intent); }
        catch(Exception e) { listening=false; releaseRecognizer(); status.setText("Cannot start speech recognition. Check your Android speech service."); updateButtons(); }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==10 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) startListening();
        else status.setText("Microphone permission denied. You can type below or enable it in Android app settings.");
    }
    private void submit(String words) {
        if(words.trim().isEmpty()) { status.setText("Speak or type a question first."); return; }
        if(prefs.getString("url","").isEmpty()) { showSettings(); return; }
        final URL url;
        try { url=ChatApi.endpoint(prefs.getString("url",""),prefs.getBoolean("http",false)); }
        catch(Exception e) { status.setText(e.getMessage()); return; }
        cancelSilently(); final int token=generation;
        final String model=prefs.getString("model",""), key=apiKey;
        finalAnswer=""; answer.setText(""); waiting=true; status.setText("Waiting for the AI…"); updateButtons();
        job=executor.submit(() -> {
            HttpURLConnection connection=null;
            try {
                connection=(HttpURLConnection)url.openConnection();
                if(token!=generation) return;
                activeConnection=connection;
                connection.setConnectTimeout(20000); connection.setReadTimeout(120000); connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setRequestProperty("Content-Type","application/json; charset=utf-8"); connection.setRequestProperty("Accept","application/json");
                if(!key.isEmpty()) connection.setRequestProperty("Authorization","Bearer "+key);
                byte[] body=ChatApi.request(model,words).toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try(java.io.OutputStream out=connection.getOutputStream()) { out.write(body); }
                int code=connection.getResponseCode();
                if(code<200||code>=300) throw new Exception(code==401||code==403 ? "Server rejected authentication. Check your API key and access." : "Server returned HTTP "+code+". Check the URL and model in Server settings.");
                String result=ChatApi.answer(ChatApi.read(connection.getInputStream()));
                runOnUiThread(() -> {
                    if(token!=generation||destroyed) return;
                    waiting=false; finalAnswer=result; answer.setText(result); speak(result);
                });
            } catch(Exception e) {
                String message=e instanceof java.net.SocketTimeoutException ? "Server timed out. Tap to try again." : e instanceof java.io.IOException ? "Could not reach the server. Check its address and your connection." : e.getMessage();
                runOnUiThread(() -> { if(token!=generation||destroyed) return; waiting=false; status.setText(message==null?"Request failed. Please try again.":message); updateButtons(); });
            } finally { if(connection!=null) connection.disconnect(); if(token==generation) activeConnection=null; }
        });
    }
    private void speak(String text) {
        if(!ttsReady) { status.setText("Answer ready. Voice is unavailable; enable a text-to-speech engine in Android settings."); updateButtons(); return; }
        if(text.isEmpty()) return;
        tts.stop(); speaking=true; status.setText("Speaking the answer…"); updateButtons();
        List<String> chunks=AnswerText.chunks(text,Math.min(3000,TextToSpeech.getMaxSpeechInputLength()-1));
        for(int i=0;i<chunks.size();i++) {
            String id=generation+":"+i+(i==chunks.size()-1?":last":"");
            if(tts.speak(chunks.get(i),i==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,null,id)==TextToSpeech.ERROR) { tts.stop(); speaking=false; status.setText("Could not play voice. Your answer is shown above."); updateButtons(); break; }
        }
    }
    private void finishSpeech(String id,boolean error) {
        runOnUiThread(() -> { if(destroyed||!id.startsWith(generation+":")) return; speaking=false; if(error&&tts!=null) tts.stop(); status.setText(error?"Voice playback failed. Your answer is shown above.":"Ready. Tap to ask a new question."); updateButtons(); });
    }
    private void releaseRecognizer() { if(recognizer!=null) { recognizer.destroy(); recognizer=null; } }
    private void cancelSilently() {
        generation++; listening=false; waiting=false; speaking=false;
        if(recognizer!=null) { recognizer.cancel(); releaseRecognizer(); }
        if(tts!=null) tts.stop();
        if(job!=null) job.cancel(true);
        HttpURLConnection connection=activeConnection; activeConnection=null;
        if(connection!=null) new Thread(connection::disconnect,"cancel-request").start();
    }
    private void cancel() { cancelSilently(); status.setText("Stopped. Tap to start a fresh question."); updateButtons(); }
    @Override protected void onStop() { super.onStop(); cancelSilently(); status.setText("Ready. Tap to ask a new question."); updateButtons(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("transcript",transcript.getText().toString()); out.putString("answer",finalAnswer); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { destroyed=true; cancelSilently(); if(tts!=null) tts.shutdown(); executor.shutdownNow(); super.onDestroy(); }
}
