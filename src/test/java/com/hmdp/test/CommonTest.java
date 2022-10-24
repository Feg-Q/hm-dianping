package com.hmdp.test;

import org.junit.jupiter.api.Test;

/**
 * @author Feg
 * @version 1.0
 */
public class CommonTest {

    @Test
    void stringTest(){
        String string = new StringBuilder("计算机").append("软件").toString();
        System.out.println(string.hashCode());
        System.out.println(string.intern());
        System.out.println(string.intern() == string);
    }
    @Test
    void testSeplite(){
        String s = "sadhau.sdaf.afa.adaf";
        System.out.println(s.substring(0, s.length()));
    }
}
