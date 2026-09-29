package com.kitty.ai;

import org.junit.Test;
import static org.junit.Assert.*;

public class V03Test {
    @Test public void ordinalVideoIsNotANewSearch(){
        Action a=Router.parse("Hey Kitty play the first song");assertEquals("select_result",a.kind);assertEquals("1",a.target);
        assertEquals("3",Router.parse("tap the third video").target);
        assertEquals("youtube_search",Router.parse("play first song by Adele").kind);
        assertNull(Router.parse("how do I play the first song?"));
    }
    @Test public void onlyVideoLikeTargetsQualify(){
        assertTrue(ScreenTargets.isVideo("A favourite song, 100 views, 2 days ago","",500,200));
        assertFalse(ScreenTargets.isVideo("Search","",500,100));
        assertFalse(ScreenTargets.isVideo("Sponsored video with 100 views yesterday","",500,200));
        assertFalse(ScreenTargets.isVideo("A favourite song, 100 views, 2 days ago","",50,30));
    }
    @Test public void updateMustBeImmutableAndChecksummed(){
        AppUpdater.validateDownload(new AppUpdater.UpdateInfo(31,"0.3.1","https://github.com/psychspy7/KITTY.AI/releases/download/v0.3.1/KITTY-AI-release.apk","a".repeat(64),""));
        for(String url:new String[]{"http://github.com/psychspy7/KITTY.AI/releases/download/v0.3.1/KITTY-AI-release.apk","https://evil.example/app.apk","https://github.com/psychspy7/KITTY.AI/releases/latest/download/KITTY-AI-release.apk"}){
            try{AppUpdater.validateDownload(new AppUpdater.UpdateInfo(31,"0.3.1",url,"a".repeat(64),""));fail();}catch(IllegalArgumentException expected){}
        }
    }
}
