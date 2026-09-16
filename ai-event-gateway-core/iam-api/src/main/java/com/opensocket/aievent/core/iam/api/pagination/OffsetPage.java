package com.opensocket.aievent.core.iam.api.pagination;
import java.util.List;
public record OffsetPage<T>(List<T> items,int page,int size,boolean hasMore,Long totalCount){public OffsetPage{items=items==null?List.of():List.copyOf(items);if(page<0||size<1||size>100)throw new IllegalArgumentException("invalid page");}}
