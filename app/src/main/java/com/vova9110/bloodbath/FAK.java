package com.vova9110.bloodbath;

public class FAK {//Floating alarm key
    int index;
    int modifier = 0;
    public FAK(int index, int modifier){
        this.index = index;
        this.modifier = modifier;
    }
    public FAK(int index){
        this.index = index;
    }
    public FAK() { }
}
