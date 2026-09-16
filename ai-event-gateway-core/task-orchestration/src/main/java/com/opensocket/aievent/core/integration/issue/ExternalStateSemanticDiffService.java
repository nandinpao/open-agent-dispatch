package com.opensocket.aievent.core.integration.issue;

import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.opensocket.aievent.core.integration.issue.webhook.ExternalIssueConflictClassification;

/** Creates an operator-usable field-path diff; falls back to hash evidence when desired JSON is not persisted. */
@Component
public class ExternalStateSemanticDiffService {
    private static final int MAX_FIELDS = 200;
    private final ObjectMapper json;
    public ExternalStateSemanticDiffService(ObjectMapper json) { this.json = json; }

    public ExternalStateSemanticDiff compare(String desiredDocumentJson,String desiredHash,String previousObservedJson,
            String currentObservedJson,String currentHash,ExternalIssueConflictClassification classification) {
        try {
            Object left;
            String mode;
            if (desiredDocumentJson != null && !desiredDocumentJson.isBlank()) {
                left=json.readValue(desiredDocumentJson,Object.class); mode="DESIRED_TO_OBSERVED";
            } else if (previousObservedJson != null && !previousObservedJson.isBlank()) {
                left=json.readValue(previousObservedJson,Object.class); mode="PREVIOUS_OBSERVED_TO_CURRENT";
            } else {
                left=Map.of(); mode="HASH_ONLY_DESIRED_TO_OBSERVED";
            }
            Object right=json.readValue(currentObservedJson==null?"{}":currentObservedJson,Object.class);
            List<Map<String,Object>> fields=new ArrayList<>();
            collect("",left,right,fields);
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("classification",classification.name());
            result.put("comparisonMode",mode);
            result.put("desiredHash",desiredHash==null?"":desiredHash);
            result.put("observedHash",currentHash==null?"":currentHash);
            result.put("changedFieldCount",fields.size());
            result.put("fields",fields);
            String rendered=json.writeValueAsString(result);
            return new ExternalStateSemanticDiff(rendered,ExternalObservationNormalizer.sha256(rendered),fields.size(),mode);
        } catch (Exception ex) {
            String fallback="{\"classification\":\""+classification+"\",\"comparisonMode\":\"HASH_ONLY_DESIRED_TO_OBSERVED\",\"desiredHash\":\""+escape(desiredHash)+"\",\"observedHash\":\""+escape(currentHash)+"\",\"changedFieldCount\":0,\"fields\":[]}";
            return new ExternalStateSemanticDiff(fallback,ExternalObservationNormalizer.sha256(fallback),0,"HASH_ONLY_DESIRED_TO_OBSERVED");
        }
    }

    private void collect(String path,Object left,Object right,List<Map<String,Object>> out) {
        if (out.size()>=MAX_FIELDS || Objects.equals(left,right)) return;
        if (left instanceof Map<?,?> lm && right instanceof Map<?,?> rm) {
            Set<String> keys=new TreeSet<>(); lm.keySet().forEach(k->keys.add(String.valueOf(k))); rm.keySet().forEach(k->keys.add(String.valueOf(k)));
            for (String key:keys) { collect(path+"/"+pointer(key),lm.get(key),rm.get(key),out); if(out.size()>=MAX_FIELDS)return; }
            return;
        }
        if (left instanceof List<?> ll && right instanceof List<?> rl) {
            int max=Math.max(ll.size(),rl.size());
            for(int i=0;i<max;i++){collect(path+"/"+i,i<ll.size()?ll.get(i):null,i<rl.size()?rl.get(i):null,out);if(out.size()>=MAX_FIELDS)return;}
            return;
        }
        Map<String,Object> field=new LinkedHashMap<>();
        field.put("path",path.isBlank()?"/":path); field.put("desired",left); field.put("observed",right); field.put("changeType",left==null?"ADDED":right==null?"REMOVED":"CHANGED");
        out.add(field);
    }
    private String pointer(String value){return value.replace("~","~0").replace("/","~1");}
    private String escape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"");}
}
