package com.github.cschen1205.tensorflow.search.models;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.cschen1205.tensorflow.classifiers.audio.models.AudioEncoder;
import com.github.cschen1205.tensorflow.classifiers.audio.models.cifar10.Cifar10AudioClassifier;
import com.github.cschen1205.tensorflow.classifiers.audio.utils.ResourceUtils;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
public class AudioSearchEngineImpl implements AudioSearchEngine {
    private static final Logger logger = LoggerFactory.getLogger(AudioSearchEngine.class);
    private AudioEncoder encoder;
    private List<AudioSearchEntry> database = new ArrayList<>();
    private String indexDbPath = "/tmp/music_index_db.json";

    public AudioSearchEngineImpl() {
        InputStream inputStream = ResourceUtils.getInputStream("tf_models/cifar10.pb");
        Cifar10AudioClassifier classifier = new Cifar10AudioClassifier();
        try {
            classifier.load_model(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        encoder = classifier;
    }


    @Override
    public void purgeDb() {
        database.clear();
    }

    @Override
    public AudioSearchEntry index(File file) {
        logger.info("indexing file: " + file.getAbsolutePath());
        float[] result = encoder.encode_audio(file);
        AudioSearchEntry entry = new AudioSearchEntry(file.getAbsolutePath(), result);
        database.add(entry);
        return entry;
    }

    @Override
    public void indexAll(File[] files) {
        for(File f : files) {
            index(f);
        }
    }

    @Override
    public List<AudioSearchEntry> query(File file, int pageIndex, int pageSize) {
        return query(file, pageIndex, pageSize, false);
    }

    @Override
    public List<AudioSearchEntry> query(File file, int pageIndex, int pageSize, boolean skipPerfectMatch) {
        float[] d = encoder.encode_audio(file);
        List<AudioSearchEntry> temp = new ArrayList<>();
        for(AudioSearchEntry entry : database){
            if(!entry.match(d) || !skipPerfectMatch){
                temp.add(entry.makeCopy());
            }
        }
        for(AudioSearchEntry entry : temp){
            entry.setDistance(entry.getDistanceSq(d));
        }
        temp.sort(Comparator.comparingDouble(a -> a.getDistance()));

        List<AudioSearchEntry> result = new ArrayList<>();
        for(int i = pageIndex * pageSize; i < (pageIndex+1) * pageSize; ++i){
            if(i < temp.size()){
                result.add(temp.get(i));
            }
        }

        return result;
    }

    @Override
    public boolean loadIndexDbIfExists() {
        File file = new File(indexDbPath);
        if(file.exists()){
            String json = null;
            try (Stream<String> stream = Files.lines(Paths.get(indexDbPath))) {

                //1. filter line 3
                //2. convert all content to upper case
                //3. convert it into a List
                json = stream
                        .filter(line -> !line.startsWith("line3"))
                        .map(String::toUpperCase)
                        .collect(Collectors.joining());

            } catch (IOException e) {
                e.printStackTrace();
            }

            if(json != null) {
                database.clear();
                database.addAll(JSON.parseArray(json, AudioSearchEntry.class));
            }
            return true;
        }
        return false;

    }

    @Override
    public void saveIndexDb() {
        File file = new File(indexDbPath);
        if(!file.getParentFile().exists()) {
            file.getParentFile().mkdir();
        }
        try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))){
            String json = JSON.toJSONString(database, SerializerFeature.BrowserCompatible);
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
