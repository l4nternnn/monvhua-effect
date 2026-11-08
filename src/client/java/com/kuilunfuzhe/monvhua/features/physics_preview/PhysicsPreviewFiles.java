package com.kuilunfuzhe.monvhua.features.physics_preview;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.List;

public final class PhysicsPreviewFiles {
    private PhysicsPreviewFiles(){}
    public static List<String> list(Path directory)throws IOException {
        Files.createDirectories(directory);
        try(var files=Files.list(directory)){return files.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().endsWith(".json")).limit(100).map(p->p.getFileName().toString()).sorted().toList();}
    }
    public static PhysicsPoseDocument read(Path directory,String filename)throws IOException {
        if(!filename.matches("[A-Za-z0-9_-]+\\.json"))throw new IllegalArgumentException("Only a JSON base filename is allowed");
        Path base=directory.toRealPath(),file=base.resolve(filename).toRealPath();
        if(!file.startsWith(base)||!Files.isRegularFile(file))throw new IllegalArgumentException("File is outside preview directory");
        try(var in=Files.newInputStream(file)) {
            byte[] bytes=in.readNBytes(256*1024+1);
            if(bytes.length>256*1024)throw new IllegalArgumentException("Pose file exceeds 256 KiB");
            return PhysicsPoseDocument.parse(new String(bytes,StandardCharsets.UTF_8));
        }
    }
}
