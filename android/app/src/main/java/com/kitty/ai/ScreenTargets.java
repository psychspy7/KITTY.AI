package com.kitty.ai;

import java.util.Locale;

/** Conservative YouTube result filter: never select arbitrary toolbar buttons. */
final class ScreenTargets {
    static boolean isVideo(String description,String id,int width,int height){
        String d=description.toLowerCase(Locale.ROOT),v=id.toLowerCase(Locale.ROOT);
        if(width<120||height<60||d.matches(".*\\b(advertisement|sponsored|promoted)\\b.*"))return false;
        boolean named=d.length()>25;
        boolean metadata=d.contains(" views")||d.contains(" ago")||d.matches("(?s).*\\b\\d+:\\d{2}\\b.*");
        return named&&(metadata||v.contains("video")||v.contains("thumbnail"));
    }
}
