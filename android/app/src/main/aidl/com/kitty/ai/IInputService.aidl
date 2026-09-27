package com.kitty.ai;
interface IInputService {
    boolean execute(String kind, String target) = 1;
    void destroy() = 16777114;
}
