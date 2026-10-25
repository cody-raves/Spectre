package net.spectre.client.builder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Reads Sponge v2/v3 and Litematica v6/v7 without discarding block properties. */
public final class SchematicReader {
    public static final int MAX_VOLUME=1_000_000, MAX_BYTES=64*1024*1024;
    private SchematicReader() { }
    public static Schematic read(Path path) throws IOException {
        if(Files.size(path)>MAX_BYTES) throw new IOException("Schematic exceeds 64 MiB");
        byte[] raw=Files.readAllBytes(path);
        InputStream source=new ByteArrayInputStream(raw);
        if(raw.length>=2&&(raw[0]&255)==31&&(raw[1]&255)==139) source=new GZIPInputStream(source);
        byte[] bytes;
        try(InputStream input=source) { bytes=input.readNBytes(MAX_BYTES+1); }
        if(bytes.length>MAX_BYTES) throw new IOException("Expanded schematic exceeds 64 MiB");
        Map<String,Object> root;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(in.readUnsignedByte()!=10) throw new IOException("NBT root must be a compound");
            string(in); root=compound(in,0);
            if(in.available()!=0) throw new IOException("Trailing NBT data");
        }
        String file=path.getFileName().toString().toLowerCase(Locale.ROOT);
        if(file.endsWith(".litematic")) return litematic(root,path.getFileName().toString());
        if(file.endsWith(".schem")) return sponge(root,path.getFileName().toString());
        throw new IOException("Supported formats: .litematic and .schem (legacy .schematic is not supported yet)");
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) throws IOException {
        if(!(value instanceof Map)) throw new IOException("Expected NBT compound");return (Map<String,Object>)value;
    }
    private static int number(Map<String,Object> m,String key) throws IOException {
        if(!(m.get(key) instanceof Number n)) throw new IOException("Missing number: "+key);return n.intValue();
    }
    private static Schematic.Pos pos(Map<String,Object> m) throws IOException {return new Schematic.Pos(number(m,"x"),number(m,"y"),number(m,"z"));}
    private static int volume(int w,int h,int l) throws IOException {
        long v=(long)w*h*l;if(w<=0||h<=0||l<=0||w>MAX_VOLUME||h>MAX_VOLUME||l>MAX_VOLUME||v>MAX_VOLUME) throw new IOException("Invalid dimensions or more than one million blocks");return (int)v;
    }
    private static Schematic sponge(Map<String,Object> outer,String file) throws IOException {
        Map<String,Object> m=outer.containsKey("Schematic")?map(outer.get("Schematic")):outer;
        int version=number(m,"Version");if(version!=2&&version!=3) throw new IOException("Supported Sponge versions: 2 and 3");
        int w=number(m,"Width")&65535,h=number(m,"Height")&65535,l=number(m,"Length")&65535,n=volume(w,h,l);
        Map<String,Object> blocks=version==3?map(m.get("Blocks")):m;
        Map<String,Object> entries=map(blocks.get("Palette"));
        if(entries.isEmpty()||entries.size()>65536) throw new IOException("Invalid palette size");
        Map<Integer,String> palette=new HashMap<>();
        for(var e:entries.entrySet()) {
            if(!(e.getValue() instanceof Number value)||value.intValue()<0||value.intValue()>MAX_VOLUME) throw new IOException("Invalid palette index");
            if(palette.put(value.intValue(),e.getKey())!=null) throw new IOException("Duplicate palette index");
        }
        if(!(blocks.get(version==3?"Data":"BlockData") instanceof byte[] data)) throw new IOException("Missing block data");
        int[] ids=new int[n];int cursor=0;List<String> normalized=new ArrayList<>();Map<Integer,Integer> remap=new HashMap<>();
        for(int i=0;i<n;i++) {
            int value=0,shift=0,b;
            do {if(cursor>=data.length||shift>=35) throw new IOException("Invalid or truncated block varint");b=data[cursor++]&255;
                if(shift==28&&(b&240)!=0) throw new IOException("Block varint overflow");value|=(b&127)<<shift;shift+=7;}while((b&128)!=0);
            String state=palette.get(value);if(state==null) throw new IOException("Unknown block palette index "+value);
            Integer index=remap.get(value);if(index==null){index=normalized.size();remap.put(value,index);normalized.add(state);}ids[i]=index;
        }
        if(cursor!=data.length) throw new IOException("Extra block data");
        Schematic.Pos offset=new Schematic.Pos(0,0,0);
        if(m.containsKey("Offset")) {if(!(m.get("Offset") instanceof int[] a)||a.length!=3)throw new IOException("Invalid schematic offset");offset=new Schematic.Pos(a[0],a[1],a[2]);}
        String name=map(m.getOrDefault("Metadata",Map.of())).getOrDefault("Name",file).toString();
        return new Schematic(name,number(m,"DataVersion"),List.of(new Schematic.Region(name,offset,w,h,l,normalized,ids)),outer);
    }
    private static Schematic litematic(Map<String,Object> root,String file) throws IOException {
        int version=number(root,"Version");if(version!=6&&version!=7) throw new IOException("Supported Litematica versions: 6 and 7");
        List<Schematic.Region> regions=new ArrayList<>();long total=0;
        for(var e:map(root.get("Regions")).entrySet()) {
            Map<String,Object> r=map(e.getValue());Schematic.Pos signed=pos(map(r.get("Size"))),p=pos(map(r.get("Position")));
            if(signed.x()==Integer.MIN_VALUE||signed.y()==Integer.MIN_VALUE||signed.z()==Integer.MIN_VALUE)throw new IOException("Region size overflow");
            int w=Math.abs(signed.x()),h=Math.abs(signed.y()),l=Math.abs(signed.z()),n=volume(w,h,l);
            total+=n;if(total>MAX_VOLUME)throw new IOException("Combined regions exceed one million blocks");
            Schematic.Pos min=new Schematic.Pos(signed.x()<0?Math.addExact(p.x(),signed.x()+1):p.x(),signed.y()<0?Math.addExact(p.y(),signed.y()+1):p.y(),signed.z()<0?Math.addExact(p.z(),signed.z()+1):p.z());
            if(!(r.get("BlockStatePalette") instanceof List<?> entries)||entries.isEmpty()||entries.size()>65536)throw new IOException("Invalid Litematica palette");
            List<String> palette=new ArrayList<>();for(Object value:entries) {
                Map<String,Object> state=map(value);if(!(state.get("Name") instanceof String name))throw new IOException("Missing block name");
                Map<String,Object> props=map(state.getOrDefault("Properties",Map.of()));StringJoiner join=new StringJoiner(",");
                new TreeMap<>(props).forEach((key,val)->join.add(key+"="+val));palette.add(name+(props.isEmpty()?"":"["+join+"]"));
            }
            int bits=Math.max(2,32-Integer.numberOfLeadingZeros(palette.size()-1));
            if(!(r.get("BlockStates") instanceof long[] data)||data.length!=((long)n*bits+63)/64)throw new IOException("Invalid packed block array length");
            int[] ids=unpack(data,n,bits,palette.size());regions.add(new Schematic.Region(e.getKey(),min,w,h,l,palette,ids));
        }
        if(regions.isEmpty())throw new IOException("No schematic regions");
        String name=map(root.getOrDefault("Metadata",Map.of())).getOrDefault("Name",file).toString();
        return new Schematic(name,number(root,"MinecraftDataVersion"),regions,root);
    }
    static int[] unpack(long[] data,int count,int bits,int paletteSize) throws IOException {
        int[] out=new int[count];long mask=(1L<<bits)-1;
        for(int i=0;i<count;i++){long bit=(long)i*bits;int word=(int)(bit>>>6),start=(int)(bit&63);long value=data[word]>>>start;
            if(start+bits>64)value|=data[word+1]<<(64-start);int id=(int)(value&mask);
            if(id>=paletteSize)throw new IOException("Invalid Litematica palette index");out[i]=id;}return out;
    }
    private static String string(DataInputStream in) throws IOException {
        int length=in.readUnsignedShort();byte[] bytes=new byte[length];in.readFully(bytes);
        try {return java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}
        catch(java.nio.charset.CharacterCodingException e){throw new IOException("Invalid UTF-8 NBT string",e);}
    }
    private static int length(DataInputStream in,int bytesPerEntry) throws IOException {
        int n=in.readInt();if(n<0||n>MAX_BYTES/Math.max(1,bytesPerEntry)||n>in.available()/Math.max(1,bytesPerEntry))throw new IOException("Invalid NBT collection length");return n;
    }
    private static Map<String,Object> compound(DataInputStream in,int depth) throws IOException {
        Map<String,Object> map=new LinkedHashMap<>();int tag;
        while((tag=in.readUnsignedByte())!=0){String key=string(in);if(map.containsKey(key))throw new IOException("Duplicate NBT key");if(map.size()>=65536)throw new IOException("NBT compound exceeds entry limit");map.put(key,tag(in,tag,depth+1));}return map;
    }
    private static Object tag(DataInputStream in,int type,int depth) throws IOException {
        if(depth>32)throw new IOException("NBT nesting exceeds 32 levels");
        return switch(type) {
            case 1 -> in.readByte();case 2 -> in.readShort();case 3 -> in.readInt();case 4 -> in.readLong();case 5 -> in.readFloat();case 6 -> in.readDouble();
            case 7 -> {int n=length(in,1);byte[] a=new byte[n];in.readFully(a);yield a;}
            case 8 -> string(in);
            case 9 -> {int child=in.readUnsignedByte(),n=length(in,1);if(n>MAX_VOLUME)throw new IOException("NBT list exceeds entry limit");if(child==0&&n!=0)throw new IOException("Invalid NBT list type");List<Object> a=new ArrayList<>(n);for(int i=0;i<n;i++)a.add(tag(in,child,depth+1));yield a;}
            case 10 -> compound(in,depth);
            case 11 -> {int n=length(in,4);int[] a=new int[n];for(int i=0;i<n;i++)a[i]=in.readInt();yield a;}
            case 12 -> {int n=length(in,8);long[] a=new long[n];for(int i=0;i<n;i++)a[i]=in.readLong();yield a;}
            default -> throw new IOException("Unsupported NBT tag "+type);
        };
    }
}
