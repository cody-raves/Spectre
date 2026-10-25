package net.spectre.client.gui;

import java.util.*;
import net.spectre.client.config.ThemeStore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;

/** GoNuts-style render-only obfuscation, with one glyph instead of the original name length. */
public final class DonutNamePrivacy {
    private DonutNamePrivacy() {}
    public static boolean donutAddress(String address){
        if(address==null)return false;
        String host=address.trim().toLowerCase(Locale.ROOT).replaceFirst(":[0-9]+$","").replaceFirst("\\.$","");
        return host.equals("donutsmp.net")||host.endsWith(".donutsmp.net");
    }
    public static boolean enabled(){
        var mc=Minecraft.getInstance();
        return ThemeStore.get().donutNamePrivacy.get() && mc!=null && mc.getCurrentServer()!=null && donutAddress(mc.getCurrentServer().ip);
    }
    public static Component mask(Component text){
        var mc=Minecraft.getInstance();
        if(text==null||!enabled()||mc.getUser()==null)return text;
        return mask(text,mc.getUser().getName());
    }
    public static Component mask(Component text,String name){
        if(text==null||name==null||!name.matches("[A-Za-z0-9_]{1,16}"))return text;
        List<Style> styles=new ArrayList<>();List<String> parts=new ArrayList<>();
        text.visit((style,part)->{styles.add(style);parts.add(part);return Optional.empty();},Style.EMPTY);
        String full=String.join("",parts),lower=full.toLowerCase(Locale.ROOT),needle=name.toLowerCase(Locale.ROOT);
        List<Integer> starts=new ArrayList<>();
        for(int at=lower.indexOf(needle);at>=0;at=lower.indexOf(needle,at+needle.length())){
            int end=at+needle.length();
            if((at==0||!accountChar(full.charAt(at-1)))&&(end==full.length()||!accountChar(full.charAt(end))))starts.add(at);
        }
        if(starts.isEmpty())return text;
        var result=Component.empty();int offset=0,match=0,skipUntil=-1;
        for(int i=0;i<parts.size();i++){
            String part=parts.get(i);int segment=0;
            for(int j=0;j<part.length();j++){
                int position=offset+j;
                if(position<skipUntil){segment=j+1;continue;}
                if(match<starts.size()&&position==starts.get(match)){
                    if(j>segment)result.append(Component.literal(part.substring(segment,j)).setStyle(styles.get(i)));
                    result.append(Component.literal("X").setStyle(styles.get(i).withObfuscated(true).withHoverEvent(null).withClickEvent(null)));
                    skipUntil=position+needle.length();match++;segment=j+1;
                }
            }
            if(segment<part.length())result.append(Component.literal(part.substring(segment)).setStyle(styles.get(i)));
            offset+=part.length();
        }
        return result;
    }
    private static boolean accountChar(char c){return c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='_';}
}
