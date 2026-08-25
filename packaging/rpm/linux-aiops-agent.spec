Name:           linux-aiops-agent
Version:        1.0.0
Release:        1%{?dist}
Summary:        Security-focused AIOps agent for Linux OS (Java edition)
License:        MIT
URL:            https://github.com/diaofenyuan/linux-agent-java
BuildArch:      noarch
Requires:       java-17-openjdk-headless

%description
Security-controlled intelligent operations agent for Linux advanced server OS.
Java/Spring Boot rewrite of diaofenyuan/Linux-agent: assets, monitoring,
alerts, service center, controlled terminal, guardrails, ChatOps, MCP tools,
structured audit hash chain and acceptance tests. No external database; local
JSON/JSONL storage only.

%prep

%build

%install
mkdir -p %{buildroot}/opt/linux-aiops-agent \
         %{buildroot}/var/lib/linux-aiops-agent \
         %{buildroot}/var/log/linux-aiops-agent \
         %{buildroot}/%{_sysconfdir}/default
install -m 644 target/linux-aiops-agent-%{version}.jar \
    %{buildroot}/opt/linux-aiops-agent/linux-aiops-agent.jar
if [[ -d frontend/build ]]; then
  mkdir -p %{buildroot}/opt/linux-aiops-agent/frontend
  cp -r frontend/build/. %{buildroot}/opt/linux-aiops-agent/frontend/
fi
install -m 644 deploy/ops-agent.service \
    %{buildroot}/%{_unitdir}/ops-agent.service
cat > %{buildroot}/%{_sysconfdir}/default/linux-aiops-agent <<EOF
OPS_AGENT_DATA_DIR=/var/lib/linux-aiops-agent
OPS_AGENT_FRONTEND_DIR=/opt/linux-aiops-agent/frontend
OPS_AGENT_PORT=18080
OPS_AGENT_BIND=127.0.0.1
EOF

%pre
getent passwd linux-agent >/dev/null || \
    useradd --system --no-create-home --shell /sbin/nologin linux-agent

%post
%systemd_post ops-agent.service
chown -R linux-agent:linux-agent /var/lib/linux-aiops-agent /var/log/linux-aiops-agent || true

%preun
%systemd_preun ops-agent.service

%postun
%systemd_postun_with_restart ops-agent.service

%files
/opt/linux-aiops-agent
/var/lib/linux-aiops-agent
/var/log/linux-aiops-agent
%{_unitdir}/ops-agent.service
%config(noreplace) %{_sysconfdir}/default/linux-aiops-agent
