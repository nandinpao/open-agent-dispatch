#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JAVAC="${JAVAC:-$(command -v javac || true)}"; JAVA="${JAVA:-$(command -v java || true)}"
if [[ -z "$JAVAC" || -z "$JAVA" ]]; then echo "[WARN] Java unavailable; P8 security classifier harness skipped"; exit 0; fi
TMP="$(mktemp -d -t p8-action-security-XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
SRC="$TMP/src"; OUT="$TMP/out"; mkdir -p "$SRC" "$OUT"
w(){ mkdir -p "$(dirname "$SRC/$1")"; cat > "$SRC/$1"; }
w org/springframework/boot/context/properties/ConfigurationProperties.java <<'JAVA'
package org.springframework.boot.context.properties; public @interface ConfigurationProperties { String prefix(); }
JAVA
w jakarta/servlet/http/HttpServletRequest.java <<'JAVA'
package jakarta.servlet.http; public interface HttpServletRequest { String getRequestURI(); String getContextPath(); String getMethod(); }
JAVA
w P8ActionSecurityHarness.java <<'JAVA'
import jakarta.servlet.http.HttpServletRequest; import com.opensocket.aievent.core.security.*;
public class P8ActionSecurityHarness {
 public static void main(String[] args){CoreInternalSecurityRequestClassifier c=new CoreInternalSecurityRequestClassifier(new CoreInternalSecurityProperties());
  require(c.requiredRole(req("GET","/admin/dispatch-governance/actions/catalog")).orElseThrow()==CoreInternalSecurityRole.OPERATOR,"read operator");
  require(c.requiredRole(req("POST","/admin/dispatch-governance/actions/proposals")).orElseThrow()==CoreInternalSecurityRole.RECOVERY_OPERATOR,"proposal operator");
  require(c.requiredRole(req("PUT","/admin/dispatch-governance/actions/catalog/ACTION_RANDOM")).orElseThrow()==CoreInternalSecurityRole.RECOVERY_ADMIN,"catalog admin");
  require(c.requiredRole(req("POST","/admin/dispatch-governance/actions/grants/grant-random/approve")).orElseThrow()==CoreInternalSecurityRole.RECOVERY_APPROVER,"grant approver");
  require(c.requiredRole(req("POST","/admin/dispatch-governance/actions/approval-requests/request-random/decide")).orElseThrow()==CoreInternalSecurityRole.RECOVERY_APPROVER,"action approver");
  require(c.requiredRole(req("POST","/admin/dispatch-governance/actions/manual-cases/case-random/acknowledge")).orElseThrow()==CoreInternalSecurityRole.RECOVERY_OPERATOR,"manual acknowledge operator");
  require(c.requiredRole(req("POST","/admin/dispatch-governance/actions/manual-cases/case-random/resolve")).orElseThrow()==CoreInternalSecurityRole.RECOVERY_ADMIN,"manual resolve admin");
  System.out.println("P8 action security classifier harness passed"); }
 static HttpServletRequest req(String m,String u){return new HttpServletRequest(){public String getMethod(){return m;}public String getRequestURI(){return u;}public String getContextPath(){return "";}};} static void require(boolean ok,String m){if(!ok)throw new IllegalStateException(m);}
}
JAVA
STUBS=()
while IFS= read -r stub; do
  STUBS+=("${stub}")
done < <(find "$SRC" -name '*.java' | sort)
"$JAVAC" -d "$OUT" "${STUBS[@]}" \
 "$ROOT/ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityRole.java" \
 "$ROOT/ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityProperties.java" \
 "$ROOT/ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityRequestClassifier.java"
"$JAVA" -cp "$OUT" P8ActionSecurityHarness
