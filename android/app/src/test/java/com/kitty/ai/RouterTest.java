package com.kitty.ai;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouterTest {
    @Test public void youtubeQueryRetainsWords(){Action a=Router.parse("Hey Kitty open utube play dark ambient music");assertEquals("youtube_search",a.kind);assertEquals("dark ambient music",a.target);}
    @Test public void messageBodyIsNotAnotherCommand(){Action a=Router.parse("message Mom on WhatsApp saying call Dad");assertEquals("Mom",a.target);assertEquals("call Dad",a.text);assertEquals("whatsapp",a.kind);}
    @Test public void directNumberRetained(){assertEquals("+919876543210",Router.parse("call +919876543210").target);}
    @Test public void explanationIsNotAction(){assertNull(Router.parse("How do I call Mom?"));assertNull(Router.parse("Someone said open settings"));}
    @Test public void localMemoryUsesBrain(){assertNull(Router.parse("remember that I like tea"));}
    @Test public void phoneNavigation(){assertEquals("recents",Router.parse("open recent apps").target);assertEquals("lock",Router.parse("lock the phone").target);}
    @Test public void tapsRequireExplicitCommand(){assertEquals("Send",Router.parse("tap Send").target);assertNull(Router.parse("What does tap Send mean?"));}
    @Test public void typedTextPreserved(){assertEquals("Hello, Sir!",Router.parse("type Hello, Sir!").text);}
    @Test public void localIndianNumberNormalization(){assertEquals("+919876543210",Router.internationalNumber("09876 543210","91"));assertEquals("+919876543210",Router.internationalNumber("98765-43210","91"));}
    @Test public void internationalNormalization(){assertEquals("+442079460000",Router.internationalNumber("+44 20 7946 0000","91"));assertEquals("+442079460000",Router.internationalNumber("00442079460000","91"));}
    @Test public void ambiguousNumbersAreNotInvented(){assertNull(Router.internationalNumber("5551234","1"));assertNull(Router.internationalNumber("Mom","91"));assertNull(Router.internationalNumber("12345","91"));}
    @Test public void weatherGoesToLiveData(){assertNull(Router.parse("weather in Delhi"));}
    @Test public void wakeWordAcceptsLikelyIndianEnglishTranscripts(){assertTrue(Router.hasWakePhrase("hey kitty"));assertTrue(Router.hasWakePhrase("hey kitti open youtube"));assertTrue(Router.hasWakePhrase("okay cutie battery"));assertFalse(Router.hasWakePhrase("a cute cat"));}
}
