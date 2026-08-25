package dev.alastorkaneki.cursedkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;

public final class CursedKeyboardService extends InputMethodService {
    private enum Mode {
        NORMAL("ABC"), GLYPH("Ȝ"), NULLFRACTURE("↯"), STYLED("𝓕"),
        SYMBOLS("#+="), NUMBERS("123"), NULLS("¤"), CUSTOM("✦"), PC("PC");
        final String label;
        Mode(String label) { this.label = label; }
    }

    private enum Panel { NONE, EMOJI, CLIPBOARD }

    private enum TextStyle {
        FULLWIDTH("ＦＵＬＬ"), CIRCLED("ⒸⒾⓇⒸⓁⒺ"), MONO("𝙼𝙾𝙽𝙾"),
        SMALLCAPS("sᴍᴀʟʟ"), DOUBLESTRUCK("𝔻𝕆𝕌𝔹𝕃𝔼"), BOLDSCRIPT("𝓑𝓞𝓛𝓓");
        final String label;
        TextStyle(String label) { this.label = label; }
    }

    private static final String[][] LETTER_ROWS = {
            {"Q","W","E","R","T","Y","U","I","O","P"},
            {"A","S","D","F","G","H","J","K","L"},
            {"Z","X","C","V","B","N","M"}
    };

    private static final String[][] GLYPH_ROWS = {
            {"҂","Ш","⊗","Я","Ŧ","¥","µ","Ʌ","⌁","Φ"},
            {"Ȝ","§","ʭ","Ϟ","Δ","⟁","☍","Ж","Ȣ"},
            {"Ƶ","×","Ψ","V","†","Ѫ","∴"}
    };

    private static final String[][] SYMBOL_ROWS = {
            {"!","@","#","$","%","^","&","*","(",")"},
            {"~","`","|","\\","/","?","<",">","[","]"},
            {"{","}","_","-","+","=",";",":","\"","'"}
    };

    private static final String[][] NUMBER_ROWS = {
            {"1","2","3","4","5","6","7","8","9","0"},
            {"!","@","#","$","%","&","*","(",")"},
            {".",",","?","!","-","_","/","\\",":",";"}
    };

    private static final String[][] NULL_ROWS = {
            {"¤","☒","ꙮ","⸸","⧖","𖤐","∅","꩜","⦻","⋮"},
            {"☒","¤","⦻","∅","꩜","⧖","⸸","ꙮ","𖤐"},
            {"⋮","⦻","¤","꩜","∅","𖤐","☒"}
    };

    private static final String[][] CUSTOM_ROWS = {
            {"Ѫ","Ж","҂","Ψ","Δ","Φ","Я","Ŧ","Ȝ","Ȣ"},
            {"⟁","☍","Ϟ","§","†","×","¥","µ","Ʌ"},
            {"⌁","⊗","Ш","Ƶ","∴","ʭ","V"}
    };

    private static final String[][] EMOJI_PAGES = {
            {"😀","😃","😄","😁","😆","😅","😂","🤣","🥲","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚","😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🥳","😏"},
            {"😭","😤","😡","🤬","😱","😨","😰","😥","😓","🤗","🤔","🫡","🤭","🫢","🫣","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴","🤤","😵","🤯"},
            {"🔥","🩸","💀","👁️","⚡","✨","🌙","⭐","❤️","💜","🖤","🤍","💥","💫","☠️","⚔️","🗡️","🛡️","🔮","⛓️","🕯️","🌀","♾️","☯️","✝️","☢️","☣️","⚠️","✅","❌","❓","‼️"}
    };

    private static final String[] CIPHER_GLYPHS = {
            "Ȝ","†","Ψ","ʭ","⊗","Ϟ","Δ","⟁","Ʌ","☍","Ж","Ȣ","∴",
            "Ѫ","⌁","Φ","҂","Я","§","Ŧ","µ","V","Ш","×","¥","Ƶ"
    };
    private static final String[] DECOYS = {"¤","☒","ꙮ","⸸","⧖","𖤐","∅","꩜","⦻"};
    private static final String[] SPACE_MARKERS = {"/","//","⋮","⸬","::"};
    private static final String CIPHER_KEY = "NULLCROWN";
    private static final String[] SMALL_CAPS = {
            "ᴀ","ʙ","ᴄ","ᴅ","ᴇ","ꜰ","ɢ","ʜ","ɪ","ᴊ","ᴋ","ʟ","ᴍ",
            "ɴ","ᴏ","ᴘ","Q","ʀ","ꜱ","ᴛ","ᴜ","ᴠ","ᴡ","x","ʏ","ᴢ"
    };

    private LinearLayout root;
    private Mode mode = Mode.GLYPH;
    private Panel panel = Panel.NONE;
    private TextStyle textStyle = TextStyle.FULLWIDTH;
    private boolean shifted;
    private int emojiPage;
    private int cipherPosition;
    private int previousCipherValue;
    private final Deque<CipherSnapshot> cipherHistory = new ArrayDeque<>();

    private int surfaceColor, keyColor, textColor, mutedColor, accentColor, accentTextColor;
    private boolean hapticsEnabled, numberRowEnabled, amoledEnabled;
    private int keyStyle;
    private int keyHeightDp = 54;

    private static final class CipherSnapshot {
        final int position, previous, deleteUnits;
        CipherSnapshot(int position, int previous, int deleteUnits) {
            this.position = position;
            this.previous = previous;
            this.deleteUnits = deleteUnits;
        }
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(3), dp(4), dp(6));
        refreshPrefsAndTheme();
        renderKeyboard();
        return root;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        resetCipherState();
        shifted = false;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        refreshPrefsAndTheme();
        panel = Panel.NONE;
        renderKeyboard();
    }

    private void refreshPrefsAndTheme() {
        SharedPreferences prefs = getSharedPreferences("cursed_keyboard_prefs", MODE_PRIVATE);
        hapticsEnabled = prefs.getBoolean("haptics", true);
        numberRowEnabled = prefs.getBoolean("number_row", false);
        amoledEnabled = prefs.getBoolean("amoled", false);
        keyStyle = prefs.getInt("key_style", 0);
        keyHeightDp = prefs.getInt("key_height", 54);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            surfaceColor = amoledEnabled ? Color.BLACK : systemColor("system_neutral1_1000", Color.rgb(20,20,24));
            keyColor = systemColor("system_neutral2_800", Color.rgb(43,43,50));
            textColor = Color.WHITE;
            mutedColor = systemColor("system_neutral2_200", Color.rgb(198,198,208));
            accentColor = systemColor("system_accent1_400", Color.rgb(179,136,255));
        } else {
            surfaceColor = systemColor("system_neutral1_50", Color.rgb(247,245,250));
            keyColor = systemColor("system_neutral2_100", Color.rgb(229,225,233));
            textColor = Color.rgb(28,27,31);
            mutedColor = systemColor("system_neutral2_700", Color.rgb(85,82,90));
            accentColor = systemColor("system_accent1_600", Color.rgb(103,80,164));
        }
        accentTextColor = contrastText(accentColor);
        if (root != null) root.setBackgroundColor(surfaceColor);
    }

    private int systemColor(String name, int fallback) {
        int id = getResources().getIdentifier(name, "color", "android");
        if (id == 0) return fallback;
        try { return getColor(id); } catch (Exception ignored) { return fallback; }
    }

    private int contrastText(int color) {
        double l = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return l > 0.58 ? Color.BLACK : Color.WHITE;
    }

    private void renderKeyboard() {
        if (root == null) return;
        root.removeAllViews();
        root.setBackgroundColor(surfaceColor);
        root.addView(makeToolbar());
        if (panel == Panel.EMOJI) {
            root.addView(makePanelHeader("Emoji  •  page " + (emojiPage + 1) + "/" + EMOJI_PAGES.length));
            addEmojiPanel();
            root.addView(makePanelBottomRow());
            return;
        }
        if (panel == Panel.CLIPBOARD) {
            captureClipboard();
            root.addView(makePanelHeader("Clipboard  •  local history"));
            addClipboardPanel();
            root.addView(makePanelBottomRow());
            return;
        }
        root.addView(makeModeHeader());
        if (numberRowEnabled && isLetterMode()) addNumberRow();
        switch (mode) {
            case NORMAL: addLetterRows(false,false,false); break;
            case GLYPH: addLetterRows(true,false,false); break;
            case NULLFRACTURE: addLetterRows(false,true,false); break;
            case STYLED: addLetterRows(false,false,true); break;
            case SYMBOLS: addLiteralRows(SYMBOL_ROWS); break;
            case NUMBERS: addLiteralRows(NUMBER_ROWS); break;
            case NULLS: addLiteralRows(NULL_ROWS); break;
            case CUSTOM: addLiteralRows(CUSTOM_ROWS); break;
            case PC: addPcKeyboard(); break;
        }
        root.addView(makeBottomRow());
    }

    private HorizontalScrollView makeToolbar() {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(true);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(2),0,dp(2),dp(2));
        scroller.addView(bar, new HorizontalScrollView.LayoutParams(-2, dp(42)));
        addToolbarMode(bar,"ABC",Mode.NORMAL); addToolbarMode(bar,"Ȝ",Mode.GLYPH);
        addToolbarMode(bar,"↯",Mode.NULLFRACTURE); addToolbarMode(bar,"𝓕",Mode.STYLED);
        addToolbarAction(bar,"😀",panel==Panel.EMOJI,()->{ if(panel==Panel.EMOJI) emojiPage=(emojiPage+1)%EMOJI_PAGES.length; else panel=Panel.EMOJI; renderKeyboard(); });
        addToolbarAction(bar,"📋",panel==Panel.CLIPBOARD,()->{ panel=panel==Panel.CLIPBOARD?Panel.NONE:Panel.CLIPBOARD; renderKeyboard(); });
        addToolbarMode(bar,"PC",Mode.PC); addToolbarMode(bar,"#+=",Mode.SYMBOLS);
        addToolbarMode(bar,"123",Mode.NUMBERS); addToolbarMode(bar,"¤",Mode.NULLS);
        addToolbarMode(bar,"✦",Mode.CUSTOM); addToolbarAction(bar,"⚙",false,this::openSettings);
        return scroller;
    }

    private void addToolbarMode(LinearLayout bar, String label, Mode target) {
        addToolbarAction(bar,label,panel==Panel.NONE&&mode==target,()->{
            panel=Panel.NONE;
            if(target==Mode.STYLED&&mode==Mode.STYLED){ TextStyle[] styles=TextStyle.values(); textStyle=styles[(textStyle.ordinal()+1)%styles.length]; }
            else { mode=target; shifted=false; resetCipherState(); }
            renderKeyboard();
        });
    }

    private void addToolbarAction(LinearLayout bar,String label,boolean active,Runnable action){
        Button b=makeSmallKey(label,active); attachClick(b,action);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(label.length()>2?58:48),dp(38)); p.setMargins(dp(2),dp(2),dp(2),dp(2)); bar.addView(b,p);
    }

    private Button makeSmallKey(String label,boolean active){
        Button b=new Button(this); b.setText(label); b.setTextSize(label.length()>2?10f:16f); b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        int bg=active?accentColor:blend(surfaceColor,keyColor,0.72f); b.setTextColor(active?accentTextColor:mutedColor); b.setBackground(rippleBackground(bg,dp(18),false)); return b;
    }

    private TextView makePanelHeader(String text){ TextView h=new TextView(this); h.setText(text); h.setTextColor(mutedColor); h.setTextSize(11.5f); h.setGravity(Gravity.CENTER); h.setPadding(0,dp(1),0,dp(3)); return h; }

    private TextView makeModeHeader(){
        String text;
        switch(mode){
            case NORMAL:text="QWERTY  •  long-press = cursed glyph";break;
            case GLYPH:text="GLYPH  •  English labels  •  long-press = letter";break;
            case NULLFRACTURE:text="NULLFRACTURE  •  chained cipher + decoys";break;
            case STYLED:text=textStyle.label+"  •  tap 𝓕 again to change style";break;
            case SYMBOLS:text="SYMBOLS  •  long-press punctuation for alternates";break;
            case NUMBERS:text="NUMBERS";break;
            case NULLS:text="NULL / DECOY";break;
            case PC:text="PC LAYER  •  Esc / Tab / arrows / Home / End / Del";break;
            default:text="CUSTOM";break;
        }
        return makePanelHeader(text);
    }

    private boolean isLetterMode(){ return mode==Mode.NORMAL||mode==Mode.GLYPH||mode==Mode.NULLFRACTURE||mode==Mode.STYLED; }

    private void addNumberRow(){
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
        for(int i=1;i<=10;i++){ String value=i==10?"0":String.valueOf(i); Button key=makeKey(value,16f,false); attachClick(key,()->commit(value)); row.addView(key,keyLayoutParams(1f,keyHeightDp-8)); }
        root.addView(row);
    }

    private void addLetterRows(boolean glyphMode,boolean cipherMode,boolean styledMode){
        for(int r=0;r<LETTER_ROWS.length;r++){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
            if(r==1) row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(keyHeightDp),0.45f));
            else if(r==2){ Button shift=makeKey("⇧",20f,shifted); attachClick(shift,()->{ shifted=!shifted; renderKeyboard(); }); row.addView(shift,keyLayoutParams(1.3f)); }
            for(int i=0;i<LETTER_ROWS[r].length;i++){
                final String letter=LETTER_ROWS[r][i], glyph=GLYPH_ROWS[r][i]; String label; float size;
                if(glyphMode){ label=(shifted?letter:letter.toLowerCase())+"\n"+glyph; size=15.2f; }
                else if(cipherMode){ label=(shifted?letter:letter.toLowerCase())+"\n↯"; size=15.2f; }
                else if(styledMode){ label=(shifted?letter:letter.toLowerCase())+"\n"+styledLetter(letter); size=14.2f; }
                else { label=shifted?letter:letter.toLowerCase(); size=20f; }
                Button key=makeKey(label,size,false);
                if(glyphMode){ attachClick(key,()->commit(glyph)); attachLongClick(key,()->commit(shifted?letter:letter.toLowerCase())); }
                else if(cipherMode){ attachClick(key,()->commitNullfracture(letter)); attachLongClick(key,()->commit(shifted?letter:letter.toLowerCase())); }
                else if(styledMode){ attachClick(key,()->commit(styledLetter(letter))); attachLongClick(key,()->commit(shifted?letter:letter.toLowerCase())); }
                else { attachClick(key,()->commit(shifted?letter:letter.toLowerCase())); attachLongClick(key,()->commit(glyph)); }
                row.addView(key,keyLayoutParams(1f));
            }
            if(r==1) row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(keyHeightDp),0.45f));
            else if(r==2){ Button bs=makeKey("⌫",21f,false); attachClick(bs,this::handleBackspace); attachLongClick(bs,this::deleteWord); row.addView(bs,keyLayoutParams(1.3f)); }
            root.addView(row);
        }
    }

    private String styledLetter(String letter){
        int index=letter.charAt(0)-'A'; boolean upper=shifted;
        switch(textStyle){
            case FULLWIDTH:return String.valueOf((char)((upper?'Ａ':'ａ')+index));
            case CIRCLED:return String.valueOf((char)((upper?'Ⓐ':'ⓐ')+index));
            case MONO:{ int base=upper?0x1D670:0x1D68A; return new String(Character.toChars(base+index)); }
            case SMALLCAPS:return SMALL_CAPS[index];
            case DOUBLESTRUCK:{ int base=upper?0x1D538:0x1D552; int cp=base+index; if(upper){ if(index==2)cp=0x2102; else if(index==7)cp=0x210D; else if(index==13)cp=0x2115; else if(index==15)cp=0x2119; else if(index==16)cp=0x211A; else if(index==17)cp=0x211D; else if(index==25)cp=0x2124; } return new String(Character.toChars(cp)); }
            default:{ int base=upper?0x1D4D0:0x1D4EA; return new String(Character.toChars(base+index)); }
        }
    }

    private void addLiteralRows(String[][] rows){
        for(String[] values:rows){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER); float spacer=Math.max(0f,(10f-values.length)/2f);
            if(spacer>0f) row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(keyHeightDp),spacer));
            for(String value:values){ Button key=makeKey(value,value.length()>2?14f:19f,false); attachClick(key,()->commit(value)); if(".".equals(value))attachLongClick(key,()->commit("?")); else if(",".equals(value))attachLongClick(key,()->commit(";")); else if("-".equals(value))attachLongClick(key,()->commit("—")); row.addView(key,keyLayoutParams(1f)); }
            if(spacer>0f) row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(keyHeightDp),spacer)); root.addView(row);
        }
    }

    private void addPcKeyboard(){
        String[][] pc={{"ESC","TAB","HOME","↑","END"},{"CTRL","ALT","←","↓","→"},{"PG↑","PG↓","DEL","INS","BKSP"}};
        for(String[] values:pc){ LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER); for(String value:values){ Button key=makeKey(value,value.length()>3?12f:16f,false); attachClick(key,()->handlePcKey(value)); row.addView(key,keyLayoutParams(1f)); } root.addView(row); }
    }

    private void handlePcKey(String value){
        switch(value){
            case "ESC":sendKey(KeyEvent.KEYCODE_ESCAPE);break; case "TAB":sendKey(KeyEvent.KEYCODE_TAB);break; case "HOME":sendKey(KeyEvent.KEYCODE_MOVE_HOME);break; case "END":sendKey(KeyEvent.KEYCODE_MOVE_END);break;
            case "↑":sendKey(KeyEvent.KEYCODE_DPAD_UP);break; case "↓":sendKey(KeyEvent.KEYCODE_DPAD_DOWN);break; case "←":sendKey(KeyEvent.KEYCODE_DPAD_LEFT);break; case "→":sendKey(KeyEvent.KEYCODE_DPAD_RIGHT);break;
            case "PG↑":sendKey(KeyEvent.KEYCODE_PAGE_UP);break; case "PG↓":sendKey(KeyEvent.KEYCODE_PAGE_DOWN);break; case "DEL":sendKey(KeyEvent.KEYCODE_FORWARD_DEL);break; case "INS":sendKey(KeyEvent.KEYCODE_INSERT);break;
            case "BKSP":handleBackspace();break; case "CTRL":commit("^");break; case "ALT":commit("⎇");break;
        }
    }

    private void addEmojiPanel(){
        String[] items=EMOJI_PAGES[emojiPage]; for(int r=0;r<4;r++){ LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER); for(int i=0;i<8;i++){ final String emoji=items[r*8+i]; Button key=makeKey(emoji,22f,false); attachClick(key,()->commit(emoji)); row.addView(key,keyLayoutParams(1f,keyHeightDp)); } root.addView(row); }
    }

    private void captureClipboard(){
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); if(cm==null||!cm.hasPrimaryClip())return; ClipData clip=cm.getPrimaryClip(); if(clip==null||clip.getItemCount()==0)return;
        CharSequence t=clip.getItemAt(0).coerceToText(this); if(t==null)return; String value=t.toString().trim(); if(value.isEmpty())return; if(value.length()>200)value=value.substring(0,200);
        SharedPreferences prefs=getSharedPreferences("cursed_keyboard_prefs",MODE_PRIVATE); if(value.equals(prefs.getString("clip0","")))return; SharedPreferences.Editor e=prefs.edit(); for(int i=5;i>=1;i--)e.putString("clip"+i,prefs.getString("clip"+(i-1),"")); e.putString("clip0",value).apply();
    }

    private void addClipboardPanel(){
        SharedPreferences prefs=getSharedPreferences("cursed_keyboard_prefs",MODE_PRIVATE); boolean any=false;
        for(int i=0;i<6;i++){ String value=prefs.getString("clip"+i,""); if(value==null||value.isEmpty())continue; any=true; final int slot=i; final String clip=value; Button item=makeKey(clipPreview(clip),13f,false); item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); item.setPadding(dp(14),0,dp(14),0); attachClick(item,()->commit(clip)); attachLongClick(item,()->{prefs.edit().remove("clip"+slot).apply();renderKeyboard();}); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52)); lp.setMargins(dp(3),dp(3),dp(3),dp(3)); root.addView(item,lp); }
        if(!any){ TextView empty=makePanelHeader("Clipboard is empty. Copy text, then reopen 📋."); root.addView(empty,new LinearLayout.LayoutParams(-1,dp(120))); }
    }

    private String clipPreview(String clip){ String s=clip.replace('\n',' ').replace('\r',' '); return s.length()<=42?s:s.substring(0,42)+"…"; }

    private LinearLayout makePanelBottomRow(){
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
        Button back=makeKey("ABC",13f,false); attachClick(back,()->{panel=Panel.NONE;mode=Mode.NORMAL;renderKeyboard();}); row.addView(back,keyLayoutParams(1.3f));
        if(panel==Panel.EMOJI){ Button page=makeKey("NEXT",12f,false); attachClick(page,()->{emojiPage=(emojiPage+1)%EMOJI_PAGES.length;renderKeyboard();}); row.addView(page,keyLayoutParams(1.3f)); }
        else { Button clear=makeKey("CLEAR",12f,false); attachClick(clear,this::clearClipboardHistory); row.addView(clear,keyLayoutParams(1.3f)); }
        Button space=makeKey("space",12f,false); attachSpaceGesture(space); row.addView(space,keyLayoutParams(4.5f)); Button bs=makeKey("⌫",21f,false); attachClick(bs,this::handleBackspace); row.addView(bs,keyLayoutParams(1.2f)); Button enter=makeKey("↵",21f,true); attachClick(enter,this::handleEnter); row.addView(enter,keyLayoutParams(1.4f)); return row;
    }

    private void clearClipboardHistory(){ SharedPreferences.Editor e=getSharedPreferences("cursed_keyboard_prefs",MODE_PRIVATE).edit(); for(int i=0;i<6;i++)e.remove("clip"+i); e.apply(); renderKeyboard(); }

    private LinearLayout makeBottomRow(){
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER); boolean letterMode=isLetterMode();
        Button left=makeKey(letterMode?"?123":"ABC",12f,false); attachClick(left,()->{mode=letterMode?Mode.NUMBERS:Mode.NORMAL;panel=Panel.NONE;shifted=false;resetCipherState();renderKeyboard();}); row.addView(left,keyLayoutParams(1.25f));
        Button comma=makeKey(",",19f,false); attachClick(comma,()->commit(",")); attachLongClick(comma,()->commit(";")); row.addView(comma,keyLayoutParams(0.75f));
        Button space=makeKey(spaceLabel(),12f,false); attachSpaceGesture(space); row.addView(space,keyLayoutParams(4.25f));
        Button period=makeKey(".",19f,false); attachClick(period,()->commit(".")); attachLongClick(period,()->commit("?")); row.addView(period,keyLayoutParams(0.75f));
        Button enter=makeKey("↵",21f,true); attachClick(enter,this::handleEnter); row.addView(enter,keyLayoutParams(1.25f)); Button ime=makeKey("⌨",17f,false); attachClick(ime,this::showImePicker); row.addView(ime,keyLayoutParams(0.95f)); return row;
    }

    private String spaceLabel(){ switch(mode){ case GLYPH:return "Ȝ  GLYPH"; case NULLFRACTURE:return "↯  NULLFX"; case STYLED:return textStyle.label; case NULLS:return "¤  NULL"; case CUSTOM:return "✦  CUSTOM"; case SYMBOLS:return "#+="; case NUMBERS:return "123"; case PC:return "PC"; default:return "space"; } }

    private void attachSpaceGesture(Button space){
        space.setOnTouchListener(new View.OnTouchListener(){ float startX; int lastStep; boolean moved; long downTime;
            @Override public boolean onTouch(View v,MotionEvent event){
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:startX=event.getX();lastStep=0;moved=false;downTime=SystemClock.uptimeMillis();v.setPressed(true);return true;
                    case MotionEvent.ACTION_MOVE:int step=Math.round((event.getX()-startX)/dp(24)); if(step!=lastStep){int dir=step>lastStep?1:-1,count=Math.abs(step-lastStep);for(int i=0;i<count;i++)sendKey(dir>0?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT);lastStep=step;moved=true;haptic(v);}return true;
                    case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:v.setPressed(false); if(event.getActionMasked()==MotionEvent.ACTION_UP&&!moved){long held=SystemClock.uptimeMillis()-downTime;if(held>520){haptic(v);showImePicker();}else{haptic(v);if(mode==Mode.NULLFRACTURE)commitCipherSpace();else commit(" ");}}return true;
                } return true;
            }
        });
    }

    private Button makeKey(String label,float size,boolean accent){ Button b=new Button(this); b.setText(label); b.setTextColor(accent?accentTextColor:textColor); b.setTextSize(size); b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0); b.setBackground(rippleBackground(accent?accentColor:keyColor,dp(10),!accent)); return b; }

    private RippleDrawable rippleBackground(int baseColor,int radius,boolean styleAware){ GradientDrawable bg=new GradientDrawable(); int contentColor=baseColor; if(styleAware&&keyStyle==1)contentColor=Color.TRANSPARENT; bg.setColor(contentColor); bg.setCornerRadius(radius); if(styleAware&&keyStyle==2){bg.setColor(Color.TRANSPARENT);bg.setStroke(dp(1),blend(mutedColor,keyColor,0.45f));} return new RippleDrawable(ColorStateList.valueOf(withAlpha(accentColor,90)),bg,null); }
    private int withAlpha(int c,int a){return Color.argb(a,Color.red(c),Color.green(c),Color.blue(c));}
    private int blend(int a,int b,float m){m=Math.max(0f,Math.min(1f,m));return Color.rgb(Math.round(Color.red(a)*(1f-m)+Color.red(b)*m),Math.round(Color.green(a)*(1f-m)+Color.green(b)*m),Math.round(Color.blue(a)*(1f-m)+Color.blue(b)*m));}
    private LinearLayout.LayoutParams keyLayoutParams(float w){return keyLayoutParams(w,keyHeightDp);} private LinearLayout.LayoutParams keyLayoutParams(float w,int hdp){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(hdp),w);p.setMargins(dp(2),dp(3),dp(2),dp(3));return p;}
    private void attachClick(View v,Runnable a){v.setOnClickListener(x->{haptic(x);a.run();});} private void attachLongClick(View v,Runnable a){v.setOnLongClickListener(x->{haptic(x);a.run();return true;});}
    private void haptic(View v){if(hapticsEnabled)v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);} private void showImePicker(){InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(imm!=null)imm.showInputMethodPicker();}
    private void openSettings(){Intent i=new Intent(this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}

    private void sendKey(int keyCode){InputConnection c=getCurrentInputConnection();if(c==null)return;long now=SystemClock.uptimeMillis();c.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,keyCode,0));c.sendKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,keyCode,0));}

    private void commitNullfracture(String letter){
        InputConnection c=getCurrentInputConnection();if(c==null)return;int oldPosition=cipherPosition,oldPrevious=previousCipherValue,p=letter.charAt(0)-'A',i=cipherPosition+1,keyValue=CIPHER_KEY.charAt(cipherPosition%CIPHER_KEY.length())-'A';int fractured=Math.floorMod(p+keyValue+(i*i),26);if((i&1)==0)fractured=25-fractured;fractured=Math.floorMod(fractured+previousCipherValue,26);StringBuilder out=new StringBuilder(CIPHER_GLYPHS[fractured]);int decoyCount=(fractured+i)%3;for(int d=0;d<decoyCount;d++)out.append(DECOYS[(fractured+i+d*3)%DECOYS.length]);String output=out.toString();c.commitText(output,1);cipherHistory.push(new CipherSnapshot(oldPosition,oldPrevious,output.length()));cipherPosition=i;previousCipherValue=fractured;
    }
    private void commitCipherSpace(){InputConnection c=getCurrentInputConnection();if(c==null)return;String output=SPACE_MARKERS[(cipherPosition+previousCipherValue)%SPACE_MARKERS.length];c.commitText(output,1);cipherHistory.push(new CipherSnapshot(cipherPosition,previousCipherValue,output.length()));}
    private void handleBackspace(){InputConnection c=getCurrentInputConnection();if(c==null)return;if(mode==Mode.NULLFRACTURE&&!cipherHistory.isEmpty()){CipherSnapshot s=cipherHistory.pop();cipherPosition=s.position;previousCipherValue=s.previous;c.deleteSurroundingText(s.deleteUnits,0);}else c.deleteSurroundingText(1,0);}
    private void deleteWord(){InputConnection c=getCurrentInputConnection();if(c==null)return;CharSequence before=c.getTextBeforeCursor(80,0);if(before==null||before.length()==0){handleBackspace();return;}int count=0;for(int i=before.length()-1;i>=0;i--){count++;if(Character.isWhitespace(before.charAt(i))&&count>1)break;}c.deleteSurroundingText(count,0);resetCipherState();}
    private void resetCipherState(){cipherPosition=0;previousCipherValue=0;cipherHistory.clear();}
    private void commit(String text){InputConnection c=getCurrentInputConnection();if(c!=null)c.commitText(text,1);if(shifted&&(mode==Mode.NORMAL||mode==Mode.STYLED)){shifted=false;renderKeyboard();}}
    private void handleEnter(){InputConnection c=getCurrentInputConnection();EditorInfo info=getCurrentInputEditorInfo();if(c==null)return;if(info!=null){int action=info.imeOptions&EditorInfo.IME_MASK_ACTION;boolean no=(info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0;if(!no&&action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED){c.performEditorAction(action);return;}}c.commitText("\n",1);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
