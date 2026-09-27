package com.kitty.ai;
import org.junit.Test;
import static org.junit.Assert.*;
public class InputCommandsTest {
    @Test public void navigationUsesExactArgumentArray(){assertArrayEquals(new String[]{"/system/bin/input","keyevent","3"},InputCommands.arguments("navigation","home"));assertNull(InputCommands.arguments("navigation","home; reboot"));}
    @Test public void coordinatesDoNotAcceptShellText(){assertArrayEquals(new String[]{"/system/bin/input","tap","100","200"},InputCommands.arguments("tap","100 200"));assertNull(InputCommands.arguments("tap","100 200; reboot"));assertNull(InputCommands.arguments("tap","Send"));assertNull(InputCommands.arguments("type","arbitrary text"));}
}
