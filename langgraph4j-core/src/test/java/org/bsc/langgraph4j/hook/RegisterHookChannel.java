package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Reducer;

import java.util.*;
import java.util.function.Supplier;

import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

public class RegisterHookChannel implements Channel<Map<String, List<String>>> {

    private final Reducer<Map<String, List<String>>> R = (oldMap, newMap) -> {
        if (oldMap == null) return newMap;
        if (newMap == null) return oldMap;

        return mergeMap(oldMap, newMap, (oldList, newList) -> {
            Set<String> combinedList = new LinkedHashSet<>(oldList);
            combinedList.addAll(newList);
            return combinedList.stream().toList();
        });
    };

    @Override
    public Optional<Reducer<Map<String, List<String>>>> getReducer() {
        return Optional.of( R );
    }

    @Override
    public Optional<Supplier<Map<String, List<String>>>> getDefault() {
        return Optional.of( HashMap::new );
    }
}
