package com.kitty.ai;
/** Typed commands, passed as argument arrays without a shell interpreter. */
final class InputCommands {
    static String[] arguments(String kind,String target){
        if("navigation".equals(kind)){
            String code;switch(target){case "home":code="3";break;case "back":code="4";break;case "recents":code="187";break;case "lock":code="223";break;default:return null;}
            return new String[]{"/system/bin/input","keyevent",code};
        }
        if("tap".equals(kind)&&target.matches("[0-9]{1,4}[ ,]+[0-9]{1,4}")){String[] xy=target.split("[ ,]+");return new String[]{"/system/bin/input","tap",xy[0],xy[1]};}
        return null;
    }
}
