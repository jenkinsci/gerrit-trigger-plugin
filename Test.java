// vim: :ts=4:sw=4

import java.net.*;
import java.io.*;
import java.util.List;
import java.util.ArrayList;


public class Test {
	public static void main(String[] args) {
        ObjA oA = new ObjA();
        System.out.println(oA.hashCode());
        oA.add("FOOBAR");
        System.out.println(oA.hashCode());
	}
    public static class ObjA {
    private List<String> ary = new ArrayList<String>();

    public void add(String str) {
        ary.add(str);
    }
}
}
