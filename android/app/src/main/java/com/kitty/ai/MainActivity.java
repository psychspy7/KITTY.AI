package com.kitty.ai;
import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.text.InputFilter;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import org.json.JSONObject;
import java.util.*;

/** Cloud chat only. No device automation, wake listener or user configuration screens. */
public final class MainActivity extends Activity {
    private Design d;private Prefs prefs;private ChatController chat;private CloudUi cloud;
    private LinearLayout root,conversation;private ScrollView scroll;private EditText input;private Button send;private TextView status;
    private final Map<String,TextView> replies=new HashMap<>();private final Map<String,LinearLayout> tools=new HashMap<>();
    private String shownUid="",session="",authUid="";private boolean loggedIn;
    private final Runnable observer=this::render;
    private FirebaseAuth.AuthStateListener authListener;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);d=new Design(this);prefs=new Prefs(this);chat=KittyApp.chat(this);cloud=new CloudUi(this,this::render);
        getWindow().setStatusBarColor(Design.BG);getWindow().setNavigationBarColor(Design.BG);
        chat.observe(observer);
        if(KittyApp.auth()!=null){authListener=auth->{String uid=prefs.accountId();if(!uid.equals(authUid)){authUid=uid;cloud.accountChanged();prefs.clearRole();chat.accountChanged();render();}if(!uid.isEmpty())cloud.refreshAccount();};KittyApp.auth().addAuthStateListener(authListener);}
    }
    private void foundation(){
        root=d.column();root.setBackgroundColor(Design.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(d.dp(20),insets.getSystemWindowInsetTop()+d.dp(8),d.dp(20),Math.max(insets.getSystemWindowInsetBottom(),d.dp(16)));return insets;});
        setContentView(root);root.requestApplyInsets();replies.clear();tools.clear();
    }
    private ImageView logo(int size){ImageView image=new ImageView(this);image.setImageResource(com.kitty.ai.R.drawable.ic_kitty);image.setContentDescription("KITTY emblem");image.setLayoutParams(new LinearLayout.LayoutParams(d.dp(size),d.dp(size)));return image;}
    private void login(){
        foundation();LinearLayout heading=d.row();heading.addView(d.label("KITTY CORP"));root.addView(heading);d.gap(root,16);
        ScrollView body=new ScrollView(this);body.setFillViewport(true);LinearLayout content=d.column();content.setGravity(Gravity.CENTER_VERTICAL);body.addView(content);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(logo(112));d.gap(content,32);content.addView(d.label("YOUR PERSONAL COMPANION"));d.gap(content,18);
        TextView title=d.text("A little wit.\nA lot of possibility.",39,Design.TEXT);title.setTypeface(Typeface.create("serif",Typeface.NORMAL));title.setLineSpacing(d.dp(3),1);content.addView(title);d.gap(content,20);
        content.addView(d.text("Meet KITTY. A helpful friend with a mischievous streak. Think, create and talk things through.",16,Design.MUTED));d.gap(content,32);
        Button google=d.button("Continue with Google",true,cloud::signIn);content.addView(google,new LinearLayout.LayoutParams(-1,d.dp(54)));d.gap(content,12);
        content.addView(d.text(KittyApp.configured()?"One account. Your own conversations.\nNo API keys or setup needed.":"Service setup pending\nVirat is connecting Google login and the cloud service.",12,Design.MUTED));d.gap(content,24);
        root.addView(d.text("Made by Virat with the help of Kitty Corp.",11,Design.MUTED));
    }
    private void home(){
        foundation();LinearLayout header=d.row();header.addView(logo(44));LinearLayout name=d.column();name.setPadding(d.dp(12),0,0,0);name.addView(d.text("KITTY",20,Design.TEXT));name.addView(d.text("Your everyday accomplice",11,Design.MUTED));header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button inbox=d.button("Inbox",false,cloud::notices);inbox.setMinWidth(0);inbox.setMinimumWidth(0);header.addView(inbox,new LinearLayout.LayoutParams(d.dp(68),d.dp(48)));Button more=d.button("•••",false,cloud::settings);more.setMinWidth(0);more.setMinimumWidth(0);more.setContentDescription("Settings");header.addView(more,new LinearLayout.LayoutParams(d.dp(48),d.dp(48)));root.addView(header);d.gap(root,12);
        LinearLayout nav=d.row();nav.addView(d.button("+ New chat",false,()->{if(chat.busy){cloud.message("Stop the current reply before starting a new chat.");return;}chat.newChat();}));nav.addView(d.button("History",false,this::history));root.addView(nav);d.gap(root,8);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setPadding(0,d.dp(8),0,d.dp(12));conversation=d.column();scroll.addView(conversation);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=d.text("Ready",11,Design.MUTED);status.setPadding(d.dp(3),d.dp(8),0,d.dp(8));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        LinearLayout compose=d.row();compose.setPadding(d.dp(8),d.dp(6),d.dp(6),d.dp(6));compose.setBackground(d.outline(Design.CARD,22));
        input=d.field("Message KITTY…","",false);input.setBackgroundColor(android.graphics.Color.TRANSPARENT);input.setSingleLine(false);input.setMaxLines(4);input.setMinLines(1);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8000)});input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setImeOptions(EditorInfoCompat.SEND);
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfoCompat.SEND){submit();return true;}return false;});compose.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        send=d.button("Send",true,this::submit);compose.addView(send,new LinearLayout.LayoutParams(d.dp(82),d.dp(54)));root.addView(compose);d.gap(root,8);
        TextView footer=d.text("Private to your account  ·  Powered by your curiosity",10,Design.MUTED);footer.setGravity(Gravity.CENTER);root.addView(footer);
        session=prefs.session();
    }
    private static final class EditorInfoCompat {static final int SEND=android.view.inputmethod.EditorInfo.IME_ACTION_SEND;}
    private void submit(){if(chat.busy){chat.stop();return;}if(chat.send(input.getText().toString())){input.setText("");((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}}
    private void empty(){
        LinearLayout card=d.column();card.setPadding(d.dp(22),d.dp(24),d.dp(22),d.dp(24));card.setBackground(d.outline(Design.CARD,24));card.addView(d.label("GOOD TO SEE YOU"));d.gap(card,16);
        TextView title=d.text("What’s on\nyour mind, Sir?",32,Design.TEXT);title.setTypeface(Typeface.create("serif",0));card.addView(title);d.gap(card,14);card.addView(d.text("Big ideas, small dilemmas, questionable jokes. I’m here for all of it.",14,Design.MUTED));d.gap(card,20);
        for(String prompt:new String[]{"Introduce yourself","Help me plan my day","Turn my idea into something great"}){card.addView(d.button(prompt,false,()->{input.setText(prompt);input.setSelection(input.length());input.requestFocus();}));}conversation.addView(card);
    }
    private void render(){
        if(isDestroyed()||isFinishing())return;
        boolean signed=prefs.signedIn();String uid=prefs.accountId();
        if(root==null||loggedIn!=signed||!shownUid.equals(uid)){loggedIn=signed;shownUid=uid;if(signed)home();else login();}
        if(!signed)return;
        renderConversation();
    }
    private void renderConversation(){
        if(!session.equals(prefs.session())){session=prefs.session();conversation.removeAllViews();replies.clear();tools.clear();}
        if(chat.turns.isEmpty()){if(!replies.containsKey("empty")){conversation.removeAllViews();empty();replies.clear();replies.put("empty",new TextView(this));}}
        else {
            boolean atEnd=!scroll.canScrollVertically(1);
            if(replies.containsKey("empty")){conversation.removeAllViews();replies.clear();}
            for(JSONObject turn:chat.turns){String id=turn.optString("id");TextView reply=replies.get(id);
                if(reply==null){LinearLayout bubble=d.column();bubble.setPadding(d.dp(18),d.dp(18),d.dp(18),d.dp(18));bubble.setBackground(d.surface(Design.CARD,18));bubble.addView(d.label("YOU"));d.gap(bubble,8);TextView question=d.text(turn.optString("input"),15,Design.TEXT);question.setTextIsSelectable(true);bubble.addView(question);conversation.addView(bubble);d.gap(conversation,20);conversation.addView(d.label("KITTY"));d.gap(conversation,10);reply=d.text("",16,Design.TEXT);reply.setTextIsSelectable(true);conversation.addView(reply);replies.put(id,reply);LinearLayout actions=d.row();conversation.addView(actions);tools.put(id,actions);d.gap(conversation,28);}
                String value=turn.optString("reply");if(!reply.getText().toString().equals(value))reply.setText(value);
                LinearLayout actions=tools.get(id);if(!turn.optString("mode").equals("pending")&&actions.getChildCount()==0){actions.addView(d.button("Copy",false,()->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("KITTY reply",turn.optString("reply")));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();}));actions.addView(d.button("Read aloud",false,()->chat.read(turn.optString("reply"))));actions.addView(d.button("Useful",false,()->{chat.feedback(id,1,"");Toast.makeText(this,"Feedback saved",Toast.LENGTH_SHORT).show();}));}
            }
            if(atEnd)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        }
        status.setText(chat.phase);send.setText(chat.busy?"Stop":"Send");send.setEnabled(chat.loaded);input.setEnabled(chat.loaded&&!chat.busy);
    }
    private void history(){chat.archive(rows->{if(isDestroyed())return;LinearLayout content=d.column();content.setPadding(d.dp(20),d.dp(12),d.dp(20),d.dp(12));if(rows.isEmpty())content.addView(d.text("Your saved conversations will appear here.",15,Design.MUTED));for(JSONObject row:rows){content.addView(d.label("YOU"));d.gap(content,6);content.addView(d.text(row.optString("input"),15,Design.TEXT));d.gap(content,10);content.addView(d.label("KITTY"));d.gap(content,6);TextView answer=d.text(row.optString("reply"),15,Design.MUTED);answer.setTextIsSelectable(true);content.addView(answer);d.gap(content,24);}ScrollView s=new ScrollView(this);s.addView(content);new AlertDialog.Builder(this).setTitle("Your history · on this phone").setView(s).setPositiveButton("Done",null).show();});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==91&&result==RESULT_OK&&data!=null&&data.getData()!=null)cloud.saveTraining(data.getData());}
    @Override protected void onStart(){super.onStart();chat.foreground=true;}
    @Override protected void onStop(){chat.foreground=false;chat.stopAudio();super.onStop();}
    @Override protected void onDestroy(){chat.remove(observer);if(KittyApp.auth()!=null&&authListener!=null)KittyApp.auth().removeAuthStateListener(authListener);cloud.close();super.onDestroy();}
}
