package com.kitty.ai;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local actions continue to work when the laptop is offline. */
public final class Router {
    private static final String WAKE="(?:kitty|kitti|cutie)";
    private static Matcher match(String regex, String text) { return Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(text); }
    public static boolean hasWakePhrase(String text) {
        if(text==null)return false;
        return text.trim().matches("(?i)^(?:(?:hey|hi|okay|ok)\\s+)?"+WAKE+"(?:[\\s,:.!-]+.*)?$");
    }
    public static String stripWake(String text) { return text.trim().replaceFirst("(?i)^(?:(?:hey|hi|okay|ok)\\s+)?"+WAKE+"[\\s,:.!-]*", "").trim(); }
    public static Action parse(String original) {
        String s=stripWake(original).replaceFirst("(?i)^(?:please\\s+|can you\\s+|could you\\s+)", "");
        Matcher m=match("(?:open\\s+(?:youtube|utube)\\s+(?:and\\s+)?(?:play|search)\\s+|play\\s+|(?:search\\s+)?(?:youtube|utube)\\s+(?:for\\s+)?)(.+)",s);
        if(m.matches()) return new Action("youtube_search",m.group(1).replaceFirst("(?i)\\s+on\\s+(?:youtube|utube)$", "").trim(),"");
        m=match("(?:go\\s+)?(home|back)|(?:open\\s+)?(recent apps)|(?:lock(?: the)? (?:screen|phone))",s);
        if(m.matches()) return new Action("navigation",s.toLowerCase(Locale.ROOT).contains("back")?"back":s.toLowerCase(Locale.ROOT).contains("recent")?"recents":s.toLowerCase(Locale.ROOT).contains("lock")?"lock":"home","");
        m=match("open\\s+(.+)",s);
        if(m.matches()) return new Action("open_app",m.group(1).trim(),"");
        m=match("(?:call|phone|dial)\\s+(.+)",s);
        if(m.matches()) return new Action("call",m.group(1).trim(),"");
        m=match("(?:whatsapp|(?:send\\s+)?(?:a\\s+)?(?:message|msg)\\s+(?:to\\s+)?)(.+?)\\s+(?:on whatsapp\\s+)?(?:saying|say|that|:)\\s*(.+)",s);
        if(m.matches()) return new Action("whatsapp",m.group(1).trim(),m.group(2).trim());
        m=match("(?:search(?: the web)?(?: for)?|google)\\s+(.+)",s);
        if(m.matches()) return new Action("web_search",m.group(1).trim(),"");
        m=match("(?:tap|click|press)\\s+(.+)",s);
        if(m.matches()) return new Action("tap",m.group(1).trim(),"");
        m=match("(?:type|write)\\s+(.+)",s);
        if(m.matches()) return new Action("type","",m.group(1));
        m=match("scroll\\s+(up|down)",s);
        if(m.matches()) return new Action("scroll",m.group(1).toLowerCase(Locale.ROOT),"");
        if(s.equalsIgnoreCase("battery") || s.equalsIgnoreCase("battery level")) return new Action("battery","","");
        return null;
    }
    public static String internationalNumber(String input,String country) {
        if(input==null || !input.matches("[+0-9() .-]+")) return null;
        String digits=input.replaceAll("[^0-9]", "");
        if(input.trim().startsWith("+")) return digits.length()>=8 && digits.length()<=15?"+"+digits:null;
        if(digits.startsWith("00")) return digits.length()>=10 && digits.length()<=17?"+"+digits.substring(2):null;
        // The country code is explicitly configured by the owner; India is the initial value.
        if(country.equals("91") && digits.length()==11 && digits.startsWith("0")) digits=digits.substring(1);
        if(country.equals("91") && digits.length()==10) return "+91"+digits;
        if(digits.startsWith(country) && digits.length()>10 && digits.length()<=15) return "+"+digits;
        return null; // Ask for an international number instead of inventing one.
    }
}
