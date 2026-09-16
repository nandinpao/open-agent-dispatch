package com.opensocket.aievent.core.iam.token.domain;
import java.net.*;import java.util.*;
public final class CidrBlock {
 private final String notation; private final byte[] network; private final int prefixLength;
 private CidrBlock(String notation,byte[] network,int prefixLength){this.notation=notation;this.network=network;this.prefixLength=prefixLength;}
 public static CidrBlock parse(String value){String v=TokenText.required(value,"cidr",80);String[] p=v.split("/",-1);if(p.length!=2)throw new IllegalArgumentException("CIDR prefix is required");if(!p[0].matches("[0-9A-Fa-f:.]+"))throw new IllegalArgumentException("CIDR address must be an IP literal");try{InetAddress a=InetAddress.getByName(p[0]);int bits=a.getAddress().length*8;int prefix=Integer.parseInt(p[1]);if(prefix<0||prefix>bits)throw new IllegalArgumentException("invalid CIDR prefix");byte[] n=mask(a.getAddress(),prefix);return new CidrBlock(a.getHostAddress()+"/"+prefix,n,prefix);}catch(UnknownHostException|NumberFormatException e){throw new IllegalArgumentException("invalid CIDR",e);}}
 public boolean contains(String ip){if(ip==null||ip.isBlank()||!ip.matches("[0-9A-Fa-f:.]+"))return false;try{byte[] b=InetAddress.getByName(ip).getAddress();return b.length==network.length&&Arrays.equals(mask(b,prefixLength),network);}catch(UnknownHostException|IllegalArgumentException e){return false;}}
 public String notation(){return notation;} @Override public String toString(){return notation;} @Override public boolean equals(Object o){return o instanceof CidrBlock c&&notation.equals(c.notation);}@Override public int hashCode(){return notation.hashCode();}
 private static byte[] mask(byte[] source,int prefix){byte[] r=source.clone();int full=prefix/8,rem=prefix%8;if(full<r.length&&rem>0)r[full]=(byte)(r[full]& (0xFF << (8-rem)));for(int i=full+(rem>0?1:0);i<r.length;i++)r[i]=0;return r;}
}
