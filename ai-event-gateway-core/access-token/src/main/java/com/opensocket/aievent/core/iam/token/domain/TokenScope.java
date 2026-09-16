package com.opensocket.aievent.core.iam.token.domain;
import java.util.*;
public final class TokenScope {
 private final Set<String> permissions,audiences,apiPrefixes; private final Set<CidrBlock> cidrs;
 public TokenScope(Collection<String> permissions,Collection<String> audiences,Collection<String> apiPrefixes,Collection<String> allowedCidrs){this.permissions=clean(permissions,"permission",160);this.audiences=clean(audiences,"audience",160);this.apiPrefixes=prefixes(apiPrefixes);TreeSet<CidrBlock> c=new TreeSet<>(Comparator.comparing(CidrBlock::notation));if(allowedCidrs!=null)allowedCidrs.forEach(x->c.add(CidrBlock.parse(x)));this.cidrs=Collections.unmodifiableSet(c);}
 public static TokenScope oneTime(String permission){return new TokenScope(Set.of(permission),Set.of("opendispatch-iam"),Set.of("/api/session/"),Set.of());}
 public Set<String> permissions(){return permissions;}public Set<String> audiences(){return audiences;}public Set<String> apiPrefixes(){return apiPrefixes;}public Set<CidrBlock> cidrs(){return cidrs;}
 public boolean permitsAudience(String audience){return audiences.isEmpty()||(audience!=null&&!audience.isBlank()&&audiences.contains(audience));}
 public boolean permitsApiPath(String path){return apiPrefixes.isEmpty()||(path!=null&&!path.isBlank()&&apiPrefixes.stream().anyMatch(path::startsWith));}
 public boolean permitsIp(String ip){return cidrs.isEmpty()||(ip!=null&&!ip.isBlank()&&cidrs.stream().anyMatch(c->c.contains(ip)));}
 public TokenScope intersectPermissions(Set<String> authority){Set<String> x=new TreeSet<>(permissions);x.retainAll(authority==null?Set.of():authority);return new TokenScope(x,audiences,apiPrefixes,cidrs.stream().map(CidrBlock::notation).toList());}
 public void requireSubsetOf(Set<String> authority){Set<String>x=new TreeSet<>(permissions);x.removeAll(authority==null?Set.of():authority);if(!x.isEmpty())throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_SCOPE_INSUFFICIENT,"Token scope exceeds principal authority: "+x);}
 private static Set<String> clean(Collection<String> values,String name,int max){TreeSet<String> out=new TreeSet<>();if(values!=null)for(String v:values)out.add(TokenText.required(v,name,max));return Collections.unmodifiableSet(out);}
 private static Set<String> prefixes(Collection<String> values){TreeSet<String> out=new TreeSet<>();if(values!=null)for(String v:values){String x=TokenText.required(v,"apiPrefix",256);if(!x.startsWith("/"))throw new IllegalArgumentException("apiPrefix must start with /");out.add(x);}return Collections.unmodifiableSet(out);}
}
