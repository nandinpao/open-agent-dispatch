package com.opensocket.aievent.worker.configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

/** Persisted signed LKG cache. Files are re-authenticated before they can be reinstalled. */
public final class WorkerRuntimeConfigurationLkgStore {
    private final Path directory;private final ObjectMapper objectMapper;
    public WorkerRuntimeConfigurationLkgStore(String directory,ObjectMapper objectMapper){if(directory==null||directory.isBlank())throw new IllegalArgumentException("Worker LKG directory is required");this.directory=Path.of(directory).toAbsolutePath().normalize();this.objectMapper=objectMapper;}
    public void save(WorkerRuntimeConfigurationSnapshot snapshot){
        if(snapshot==null||snapshot.configSetId()==null||snapshot.configSetId().isBlank())throw new IllegalArgumentException("snapshot/configSetId is required");
        try{Files.createDirectories(directory);Path target=file(snapshot.configSetId());Path tmp=Files.createTempFile(directory,".runtime-config-lkg-",".tmp");Files.write(tmp,objectMapper.writeValueAsBytes(snapshot),StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}}catch(Exception ex){throw new IllegalStateException("RUNTIME_CONFIGURATION_LKG_PERSIST_FAILED configSetId="+snapshot.configSetId(),ex);}
    }
    public List<LoadResult> loadAll(){if(!Files.isDirectory(directory))return List.of();List<LoadResult> results=new ArrayList<>();try(DirectoryStream<Path> stream=Files.newDirectoryStream(directory,"*.json")){for(Path path:stream){try{var snapshot=objectMapper.readValue(Files.readAllBytes(path),WorkerRuntimeConfigurationSnapshot.class);results.add(new LoadResult(path.toString(),snapshot,null));}catch(Exception ex){results.add(new LoadResult(path.toString(),null,ex.getClass().getSimpleName()+": "+ex.getMessage()));}}}catch(Exception ex){throw new IllegalStateException("RUNTIME_CONFIGURATION_LKG_SCAN_FAILED directory="+directory,ex);}results.sort(Comparator.comparing(LoadResult::source));return List.copyOf(results);}
    public Path directory(){return directory;}
    private Path file(String configSetId){String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(configSetId.getBytes(StandardCharsets.UTF_8));return directory.resolve(encoded+".json");}
    public record LoadResult(String source,WorkerRuntimeConfigurationSnapshot snapshot,String error){public boolean readable(){return snapshot!=null&&error==null;}}
}
