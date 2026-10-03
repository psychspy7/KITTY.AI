package com.kitty.ai;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class CloudBoundaryTest {
    @Test public void httpsOriginsOnly() throws Exception {
        BrainClient.validateUrl("https://kitty.example.com");
        for(String bad:new String[]{"http://127.0.0.1:8765","http://example.com","https://user:secret@host","https://host/path","https://host?token=secret","https://host#token",""}){
            try{BrainClient.validateUrl(bad);fail(bad);}catch(java.io.IOException expected){}
        }
    }
    @Test public void releaseLinksCannotBeRetargeted(){
        assertEquals("https://github.com/psychspy7/KITTY.AI/releases/tag/v0.5.1",AppUpdater.releasePage("https://github.com/psychspy7/KITTY.AI/releases/download/v0.5.1/KITTY-AI-release.apk"));
        for(String bad:new String[]{"http://github.com/psychspy7/KITTY.AI/releases/download/v0.5.1/KITTY-AI-release.apk","https://evil.example/update.apk","https://github.com/other/repo/releases/download/v0.5.1/KITTY-AI-release.apk","https://github.com/psychspy7/KITTY.AI/releases/download/v0.5.1/KITTY-AI-release.apk?next=evil","https://github.com:8443/psychspy7/KITTY.AI/releases/download/v0.5.1/KITTY-AI-release.apk"}){
            try{AppUpdater.releasePage(bad);fail();}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void metadataMustNameARealVersion() throws Exception {
        AppUpdater.UpdateInfo info=AppUpdater.parse(new JSONObject().put("latest_version_code",51).put("version_name","0.5.1").put("download_url","https://github.com/psychspy7/KITTY.AI/releases/download/v0.5.1/KITTY-AI-release.apk"));assertEquals(51,info.code);
        try{AppUpdater.parse(new JSONObject().put("latest_version_code",0));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void accountArchiveScopesAreStableAndDifferent(){
        assertEquals(ConversationStore.pairing("https://kitty.example.com","user-a",false),ConversationStore.pairing("https://kitty.example.com","user-a",false));
        assertNotEquals(ConversationStore.pairing("https://kitty.example.com","user-a",false),ConversationStore.pairing("https://kitty.example.com","user-b",false));
        assertEquals("unpaired",ConversationStore.pairing("https://kitty.example.com","",true));
    }
}
