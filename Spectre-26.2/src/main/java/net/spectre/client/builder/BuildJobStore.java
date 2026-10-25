package net.spectre.client.builder;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Jobs are saved as plans; restoring a job never starts purchasing or movement. */
public final class BuildJobStore {
    public record Job(int schema,String id,String schematicFile,String sha256,String world,
                      Schematic.Pos origin,int rotation,MaterialPlan materials,String state) { }
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public static Job create(Path schematic,String world,Schematic.Pos origin,int rotation,MaterialPlan materials) throws IOException {
        return new Job(1,UUID.randomUUID().toString(),schematic.getFileName().toString(),hash(schematic),world,origin,Math.floorMod(rotation,4),materials,"PLANNED");
    }
    public static void save(Path directory,Job job) throws IOException {
        Files.createDirectories(directory);UUID.fromString(job.id());Path target=directory.resolve(job.id()+".json"),temp=Files.createTempFile(directory,"build-",".tmp");
        try {Files.writeString(temp,JSON.toJson(job),StandardCharsets.UTF_8);
            try {Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    public static Job load(Path path,Path schematics) throws IOException {
        if(Files.size(path)>4*1024*1024)throw new IOException("Build job exceeds size limit");
        try {
            Job j=JSON.fromJson(Files.readString(path),Job.class);
            if(j==null||j.schema()!=1||j.id()==null||j.origin()==null||j.materials()==null||j.world()==null||!"PLANNED".equals(j.state())||j.rotation()<0||j.rotation()>3)throw new IOException("Unsupported or incomplete build job");
            UUID.fromString(j.id());Path root=schematics.toRealPath(),source=root.resolve(j.schematicFile()).normalize().toRealPath();
            if(!source.getParent().equals(root)||!hash(source).equals(j.sha256()))throw new IOException("Schematic has changed or is outside the schematic folder");
            return j;
        } catch(JsonParseException|IllegalArgumentException|NullPointerException e){throw new IOException("Invalid build job",e);}
    }
    public static String hash(Path path) throws IOException {
        try {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(path)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return HexFormat.of().formatHex(digest.digest());}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
