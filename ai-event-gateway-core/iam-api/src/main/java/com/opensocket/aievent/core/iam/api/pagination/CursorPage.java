package com.opensocket.aievent.core.iam.api.pagination;
import java.util.List;
public record CursorPage<T>(List<T> items,String nextCursor,boolean hasMore){public CursorPage{items=items==null?List.of():List.copyOf(items);nextCursor=nextCursor==null?"":nextCursor;}}
