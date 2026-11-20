import { LitElement, html, css, nothing } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/select';
import '@vaadin/progress-bar';

const TOPOLOGY = {
    AI_AGENT: { label: 'AI', css: 'ai' },
    NON_AI_AGENT: { label: 'Action', css: 'nonai' },
    HUMAN_IN_THE_LOOP: { label: 'Human', css: 'human' },
    SEQUENCE: { label: 'Sequence', css: 'seq' },
    PARALLEL: { label: 'Parallel', css: 'par' },
    LOOP: { label: 'Loop', css: 'loop' },
    ROUTER: { label: 'Router', css: 'rtr' },
    STAR: { label: 'Star', css: 'star' },
};

const ALL_SESSIONS = '__all__';

function topologyOf(name) {
    return TOPOLOGY[name] || TOPOLOGY.AI_AGENT;
}

function truncate(text, max) {
    if (text == null) {
        return '';
    }
    return text.length <= max ? text : text.substring(0, max) + '...';
}

function fmtDur(ms) {
    if (ms < 1000) {
        return ms + 'ms';
    }
    if (ms < 60000) {
        return (ms / 1000).toFixed(1) + 's';
    }
    return Math.floor(ms / 60000) + 'm ' + Math.floor((ms % 60000) / 1000) + 's';
}

function fmtTokens(tokens) {
    return tokens < 1000 ? String(tokens) : (tokens / 1000).toFixed(1) + 'k';
}

function mapToString(map) {
    const entries = Object.entries(map || {});
    if (entries.length === 0) {
        return '{}';
    }
    return '{' + entries.map(([k, v]) => k + '=' + truncate(String(v), 60)).join(', ') + '}';
}

/**
 * Opens the HTML returned by the given JSON-RPC call in a new browser tab. The tab is opened synchronously so the
 * click still counts as a user gesture for popup blockers.
 */
function openHtmlReport(call) {
    const tab = window.open('', '_blank');
    call.then(response => {
        const url = URL.createObjectURL(new Blob([response.result], { type: 'text/html' }));
        if (tab) {
            tab.location = url;
        } else {
            window.open(url, '_blank');
        }
    }).catch(error => {
        if (tab) {
            tab.close();
        }
        console.error('Unable to generate the LangChain4j report', error);
    });
}

export class QwcAgentsExecutions extends LitElement {

    static styles = css`
        :host {
            --c-ai: #2e8555;
            --c-nonai: #6b7280;
            --c-human: #d97706;
            --c-seq: #0891b2;
            --c-par: #3b82f6;
            --c-loop: #7c3aed;
            --c-rtr: #dc2626;
            --c-star: #ca8a04;
            --c-tool: #e74694;
            --c-brand: #2e8555;
            --c-ok: #10b981;
            --c-fail: #ef4444;
            --c-run: #3b82f6;
            --fg: var(--lumo-body-text-color);
            --fg2: var(--lumo-secondary-text-color);
            --fg3: var(--lumo-tertiary-text-color);
            --brd: var(--lumo-contrast-10pct);
            --bg2: var(--lumo-contrast-5pct);
            --card: var(--lumo-base-color);
            --conn: var(--lumo-contrast-30pct);
            --rad: 8px;
            display: flex;
            flex-direction: column;
            height: 100%;
            color: var(--fg);
            line-height: 1.6;
        }
        *, *::before, *::after { box-sizing: border-box; }
        .toolbar { display: flex; align-items: center; gap: 10px; padding: 10px 15px; flex-wrap: wrap; }
        .toolbar .root-name { font-weight: 600; }
        .toolbar .spacer { flex: 1; }
        .content { flex: 1; overflow: auto; padding: 0 15px 15px; }
        .placeholder { padding: 20px; text-align: center; color: var(--fg2); }

        .section-header {
            display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
            padding-bottom: 10px; border-bottom: 2px solid var(--c-brand);
        }
        .section-icon {
            width: 30px; height: 30px; background: var(--c-brand); border-radius: var(--rad);
            display: flex; align-items: center; justify-content: center; flex-shrink: 0;
        }
        .section-title { font-size: 21px; font-weight: 600; margin: 0; }
        .section-count { font-size: 12px; color: var(--fg3); background: var(--bg2); padding: 2px 10px; border-radius: 12px; }
        .tool-toggle {
            font-size: 11px; padding: 3px 12px; border-radius: 4px; cursor: pointer; margin-left: auto;
            border: 1px solid var(--brd); background: var(--bg2); color: var(--fg2);
        }
        .tool-toggle:hover { background: var(--brd); }
        .no-data { padding: 20px; text-align: center; color: var(--fg3); }

        .execution-group { margin-bottom: 12px; }
        .execution-header {
            display: flex; align-items: center; gap: 10px; padding: 10px 16px; background: var(--card);
            border: 1px solid var(--brd); border-radius: var(--rad) var(--rad) 0 0; cursor: pointer; user-select: none;
        }
        .execution-header:hover { background: var(--bg2); }
        .execution-header.collapsed { border-radius: var(--rad); }
        .chevron { transition: transform .2s; color: var(--fg3); font-size: 11px; }
        .execution-header.collapsed .chevron { transform: rotate(-90deg); }
        .execution-memory-id { font-weight: 600; font-size: 14px; }
        .execution-meta { margin-left: auto; display: flex; gap: 8px; align-items: center; }
        .status-badge { padding: 2px 10px; border-radius: 12px; font-size: 11px; font-weight: 600; }
        .st-ok { background: color-mix(in srgb, var(--c-ok) 18%, var(--card)); color: color-mix(in srgb, var(--c-ok) 60%, var(--fg)); }
        .st-fail { background: color-mix(in srgb, var(--c-fail) 18%, var(--card)); color: color-mix(in srgb, var(--c-fail) 65%, var(--fg)); }
        .st-run { background: color-mix(in srgb, var(--c-run) 18%, var(--card)); color: color-mix(in srgb, var(--c-run) 65%, var(--fg)); }
        .execution-body {
            border: 1px solid var(--brd); border-top: none; border-radius: 0 0 var(--rad) var(--rad);
            background: var(--card); overflow: hidden;
        }

        .single-exec { padding: 12px 16px; border-bottom: 1px solid var(--brd); }
        .single-exec:last-child { border-bottom: none; }
        .exec-summary { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
        .status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
        .st-dot-ok { background: var(--c-ok); }
        .st-dot-fail { background: var(--c-fail); }
        .st-dot-run { background: var(--c-run); animation: pulse 1.5s infinite; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: .4; } }
        .exec-agent { font-weight: 600; font-size: 14px; }
        .dur-badge { font-size: 12px; color: var(--fg3); font-family: var(--lumo-font-family-monospace, monospace); }
        .exec-time { font-size: 11px; color: var(--fg3); }
        .error-box {
            background: color-mix(in srgb, var(--c-fail) 10%, var(--card));
            border: 1px solid color-mix(in srgb, var(--c-fail) 40%, var(--card));
            color: color-mix(in srgb, var(--c-fail) 70%, var(--fg));
            border-radius: var(--rad); padding: 8px 12px; margin-bottom: 8px; font-size: 12px;
        }
        .error-box code { font-size: 11px; word-break: break-all; }
        .error-box .root-cause { margin-top: 4px; }

        .wf-table { width: 100%; border-collapse: collapse; font-size: 13px; }
        .wf-table th {
            text-align: left; padding: 6px 10px; background: var(--bg2); color: var(--fg3); font-size: 10px;
            font-weight: 600; text-transform: uppercase; letter-spacing: .5px; border-bottom: 1px solid var(--brd);
        }
        .wf-table td { padding: 5px 10px; border-bottom: 1px solid var(--brd); vertical-align: middle; }
        .wf-table tr:last-child td { border-bottom: none; }
        .wf-table tr.wf-row { cursor: pointer; }
        .wf-table tr.wf-row:hover { background: var(--bg2); }
        .wf-table tr.wf-row.expanded { background: var(--bg2); }
        .wf-timeline-col { width: 35%; }
        .wf-agent { display: flex; align-items: center; gap: 4px; white-space: nowrap; }
        .wf-indent { display: inline-block; width: 18px; }
        .wf-connector { color: var(--conn); font-family: monospace; margin-right: 2px; font-size: 12px; }
        .wf-dur { font-family: var(--lumo-font-family-monospace, monospace); font-size: 12px; color: var(--fg2); white-space: nowrap; }
        .wf-bar-track { position: relative; height: 18px; background: var(--bg2); border-radius: 3px; min-width: 120px; }
        .wf-bar { position: absolute; top: 2px; height: 14px; border-radius: 3px; opacity: .8; min-width: 2px; }
        .wf-bar:hover { opacity: 1; }
        .bar-ai { background: var(--c-ai); }
        .bar-nonai { background: var(--c-nonai); }
        .bar-human { background: var(--c-human); }
        .bar-seq { background: var(--c-seq); }
        .bar-par { background: var(--c-par); }
        .bar-loop { background: var(--c-loop); }
        .bar-rtr { background: var(--c-rtr); }
        .bar-star { background: var(--c-star); }
        .bar-tool { background: var(--c-tool); }
        .wf-io { font-size: 11px; color: var(--fg3); white-space: nowrap; }
        .row-toggle {
            cursor: pointer; font-size: 10px; margin-right: 4px; display: inline-block;
            transition: transform .2s; color: var(--fg3); user-select: none;
        }
        .row-toggle.collapsed { transform: rotate(-90deg); }

        .topology-badge {
            display: inline-block; padding: 0 5px; border-radius: 4px; font-size: 9px; font-weight: 700;
            text-transform: uppercase; letter-spacing: .4px; color: #fff;
        }
        .topology-badge.ai { background: var(--c-ai); }
        .topology-badge.nonai { background: var(--c-nonai); }
        .topology-badge.human { background: var(--c-human); }
        .topology-badge.seq { background: var(--c-seq); }
        .topology-badge.par { background: var(--c-par); }
        .topology-badge.loop { background: var(--c-loop); }
        .topology-badge.rtr { background: var(--c-rtr); }
        .topology-badge.star { background: var(--c-star); }
        .topology-badge.tool { background: var(--c-tool); }
        .iter-tag, .failed-tag {
            font-size: 9px; padding: 0 5px; border-radius: 3px; font-weight: 600; white-space: nowrap;
            background: color-mix(in srgb, var(--c-loop) 14%, var(--card));
            color: color-mix(in srgb, var(--c-loop) 75%, var(--fg));
            border: 1px solid color-mix(in srgb, var(--c-loop) 45%, var(--card));
        }
        .failed-tag {
            background: color-mix(in srgb, var(--c-fail) 14%, var(--card));
            color: color-mix(in srgb, var(--c-fail) 75%, var(--fg));
            border-color: color-mix(in srgb, var(--c-fail) 45%, var(--card));
        }

        .detail-row td { background: var(--bg2); padding: 8px 10px 12px 40px; }
        .detail { display: grid; grid-template-columns: max-content 1fr; gap: 4px 12px; font-size: 12px; }
        .detail dt { color: var(--fg3); font-weight: 600; }
        .detail dd { margin: 0; }
        .detail pre {
            margin: 0; white-space: pre-wrap; word-break: break-word; font-size: 11px;
            font-family: var(--lumo-font-family-monospace, monospace); color: var(--fg);
        }
        .footer { text-align: center; padding: 16px 0 4px; font-size: 12px; color: var(--fg3); border-top: 1px solid var(--brd); margin-top: 20px; }
    `;

    static properties = {
        _entries: { state: true },
        _selectedIndex: { state: true },
        _report: { state: true },
        _loading: { state: true },
        _error: { state: true },
        _sessionFilter: { state: true },
        _toolsHidden: { state: true },
        _collapsedGroups: { state: true },
        _collapsedRows: { state: true },
        _expandedRows: { state: true },
        _loadedAt: { state: true },
    };

    jsonRpc = new JsonRpc(this);

    constructor() {
        super();
        this._entries = [];
        this._selectedIndex = 0;
        this._report = null;
        this._loading = true;
        this._error = null;
        this._sessionFilter = ALL_SESSIONS;
        this._toolsHidden = false;
        this._collapsedGroups = new Map();
        this._collapsedRows = new Set();
        this._expandedRows = new Set();
        this._loadedAt = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadEntries();
    }

    _loadEntries() {
        this.jsonRpc.getRootAgentEntries()
            .then(response => {
                this._entries = response.result || [];
                this._selectedIndex = this._entries.length > 0 ? this._entries[0].index : 0;
                this._loadExecutions();
            })
            .catch(error => {
                this._error = String(error);
                this._loading = false;
            });
    }

    _loadExecutions() {
        this._loading = true;
        this._error = null;
        this.jsonRpc.getExecutions({ index: this._selectedIndex })
            .then(response => {
                const result = response.result;
                if (result.error) {
                    this._error = result.error;
                    this._report = null;
                } else {
                    this._report = result;
                    if (this._sessionFilter !== ALL_SESSIONS
                        && !result.sessions.some(s => s.memoryId === this._sessionFilter)) {
                        this._sessionFilter = ALL_SESSIONS;
                    }
                }
                this._loadedAt = new Date();
                this._loading = false;
            })
            .catch(error => {
                this._error = String(error);
                this._loading = false;
            });
    }

    _onRootSelected(e) {
        const index = parseInt(e.target.value, 10);
        if (!isNaN(index) && index !== this._selectedIndex) {
            this._selectedIndex = index;
            this._sessionFilter = ALL_SESSIONS;
            this._collapsedGroups = new Map();
            this._collapsedRows = new Set();
            this._expandedRows = new Set();
            this._loadExecutions();
        }
    }

    _openReport() {
        openHtmlReport(this.jsonRpc.getExecutionReportHtml({ index: this._selectedIndex }));
    }

    render() {
        return html`
            ${this._renderToolbar()}
            <div class="content">
                ${this._loading && !this._report ? html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`
                    : this._error ? html`<div class="placeholder">${this._error}</div>`
                    : this._renderReport()}
            </div>
        `;
    }

    _renderToolbar() {
        const roots = this._entries.map(e => ({ label: e.typeName, value: String(e.index) }));
        const sessions = [{ label: 'All sessions', value: ALL_SESSIONS }];
        if (this._report) {
            for (const session of this._report.sessions) {
                sessions.push({ label: session.memoryId, value: session.memoryId });
            }
        }
        return html`
            <div class="toolbar">
                ${roots.length > 1 ? html`
                    <vaadin-select label="Root agent" .items="${roots}" .value="${String(this._selectedIndex)}"
                        @value-changed="${this._onRootSelected}"></vaadin-select>
                ` : roots.length === 1 ? html`<span class="root-name">${roots[0].label}</span>` : nothing}
                ${sessions.length > 2 ? html`
                    <vaadin-select label="Session (memoryId)" .items="${sessions}" .value="${this._sessionFilter}"
                        @value-changed="${e => { this._sessionFilter = e.target.value || ALL_SESSIONS; }}"></vaadin-select>
                ` : nothing}
                <vaadin-button theme="small" @click="${() => this._loadExecutions()}">Refresh</vaadin-button>
                <span class="spacer"></span>
                <vaadin-button theme="small tertiary" title="Open the report generated by LangChain4j in a new tab"
                    ?disabled="${this._entries.length === 0}" @click="${() => this._openReport()}">
                    <vaadin-icon icon="font-awesome-solid:up-right-from-square" slot="prefix"></vaadin-icon>
                    Open LangChain4j report
                </vaadin-button>
            </div>
        `;
    }

    _renderReport() {
        const allSessions = this._report.sessions;
        const sessions = this._sessionFilter === ALL_SESSIONS
            ? allSessions
            : allSessions.filter(s => s.memoryId === this._sessionFilter);
        return html`
            <section>
                <div class="section-header">
                    <div class="section-icon">
                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                            <rect x="1" y="3" width="14" height="2" rx="1" fill="white"/>
                            <rect x="1" y="7" width="10" height="2" rx="1" fill="white"/>
                            <rect x="1" y="11" width="12" height="2" rx="1" fill="white"/>
                        </svg>
                    </div>
                    <h2 class="section-title">Execution History</h2>
                    <span class="section-count">${allSessions.length} session(s)</span>
                    <button class="tool-toggle" @click="${() => { this._toolsHidden = !this._toolsHidden; }}">Toggle Tools</button>
                </div>
                ${sessions.length === 0
                    ? html`<div class="no-data">No executions recorded.</div>`
                    : sessions.map(s => this._renderGroup(s, allSessions.indexOf(s)))}
                <div class="footer">Loaded from the running application at ${this._loadedAt ? this._loadedAt.toLocaleTimeString() : ''}</div>
            </section>
        `;
    }

    _isGroupCollapsed(session, idx) {
        if (this._collapsedGroups.has(session.memoryId)) {
            return this._collapsedGroups.get(session.memoryId);
        }
        return this._sessionFilter === ALL_SESSIONS && idx > 0;
    }

    _toggleGroup(session, idx) {
        const groups = new Map(this._collapsedGroups);
        groups.set(session.memoryId, !this._isGroupCollapsed(session, idx));
        this._collapsedGroups = groups;
    }

    _renderGroup(session, idx) {
        const collapsed = this._isGroupCollapsed(session, idx);
        return html`
            <div class="execution-group">
                <div class="execution-header ${collapsed ? 'collapsed' : ''}" @click="${() => this._toggleGroup(session, idx)}">
                    <span class="chevron">&#9660;</span>
                    <span class="execution-memory-id">Session: ${session.memoryId}</span>
                    <div class="execution-meta">
                        <span class="section-count">${session.runs} run(s)</span>
                        ${session.success > 0 ? html`<span class="status-badge st-ok">${session.success} success</span>` : nothing}
                        ${session.failed > 0 ? html`<span class="status-badge st-fail">${session.failed} failed</span>` : nothing}
                        ${session.running > 0 ? html`<span class="status-badge st-run">in progress</span>` : nothing}
                    </div>
                </div>
                ${collapsed ? nothing : html`
                    <div class="execution-body">
                        ${session.executions.map((e, i) => this._renderExecution(e, session.memoryId + '/' + i))}
                    </div>`}
            </div>
        `;
    }

    _renderExecution(execution, key) {
        const top = execution.topLevel;
        const hasWaterfall = top.done || top.nestedInvocations.length > 0 || top.toolExecutions.length > 0;
        let durationBadge = 'running...';
        if (top.done) {
            durationBadge = fmtDur(top.durationMillis);
        } else if (execution.status === 'fail') {
            durationBadge = 'failed';
        }
        return html`
            <div class="single-exec">
                <div class="exec-summary">
                    <span class="status-dot st-dot-${execution.status}"></span>
                    <span class="exec-agent">${top.name}</span>
                    <span class="dur-badge">${durationBadge}</span>
                    ${top.done ? html`<span class="exec-time">${top.startTime} &#8594; ${top.finishTime}</span>`
                        : html`<span class="exec-time">${top.startTime} &#8594;</span>`}
                </div>
                ${execution.error ? html`
                    <div class="error-box">
                        <strong>Error in ${execution.error.agentName}:</strong>
                        <code title="${execution.error.type}">${execution.error.message}</code>
                        ${execution.error.rootCause ? html`<div class="root-cause">Caused by: <code>${execution.error.rootCause}</code></div>` : nothing}
                    </div>` : nothing}
                ${hasWaterfall ? this._renderWaterfall(top, key) : nothing}
            </div>
        `;
    }

    _renderWaterfall(top, key) {
        const base = top.startMillis;
        const totalMs = Math.max(1, top.done ? top.durationMillis : this._report.nowMillis - base);
        const rows = [];
        this._flatten(top, 0, -1, rows, { next: 0 }, []);
        return html`
            <table class="wf-table">
                <thead><tr><th>Agent</th><th>Duration</th><th>Tokens</th><th class="wf-timeline-col">Timeline</th><th>Input</th><th>Output</th></tr></thead>
                <tbody>
                    ${rows.map(row => this._isRowHidden(row, key) ? nothing : this._renderRow(row, key, base, totalMs))}
                </tbody>
            </table>
        `;
    }

    _flatten(invocation, depth, parentRowId, rows, counter, ancestors) {
        const rowId = counter.next++;
        const hasChildren = invocation.nestedInvocations.length > 0 || invocation.toolExecutions.length > 0;
        rows.push({ kind: 'agent', rowId, depth, hasChildren, ancestors, data: invocation });
        const childAncestors = ancestors.concat([rowId]);
        for (const tool of invocation.toolExecutions) {
            rows.push({ kind: 'tool', rowId: counter.next++, depth: depth + 1, hasChildren: false, ancestors: childAncestors, data: tool });
        }
        for (const nested of invocation.nestedInvocations) {
            this._flatten(nested, depth + 1, rowId, rows, counter, childAncestors);
        }
    }

    _isRowHidden(row, key) {
        if (row.kind === 'tool' && this._toolsHidden) {
            return true;
        }
        for (const ancestor of row.ancestors) {
            if (this._collapsedRows.has(key + '#' + ancestor)) {
                return true;
            }
        }
        return false;
    }

    _toggleRow(event, rowKey) {
        event.stopPropagation();
        const rows = new Set(this._collapsedRows);
        if (rows.has(rowKey)) {
            rows.delete(rowKey);
        } else {
            rows.add(rowKey);
        }
        this._collapsedRows = rows;
    }

    _toggleDetail(rowKey) {
        const rows = new Set(this._expandedRows);
        if (rows.has(rowKey)) {
            rows.delete(rowKey);
        } else {
            rows.add(rowKey);
        }
        this._expandedRows = rows;
    }

    _renderRow(row, key, base, totalMs) {
        const rowKey = key + '#' + row.rowId;
        const expanded = this._expandedRows.has(rowKey);
        const indents = [];
        for (let i = 0; i < row.depth; i++) {
            indents.push(html`<span class="wf-indent"></span>`);
        }
        return row.kind === 'tool'
            ? html`${this._renderToolRow(row.data, indents, rowKey, expanded, base, totalMs)}${expanded ? this._renderToolDetail(row.data) : nothing}`
            : html`${this._renderAgentRow(row, indents, rowKey, expanded, base, totalMs)}${expanded ? this._renderAgentDetail(row.data) : nothing}`;
    }

    _renderAgentRow(row, indents, rowKey, expanded, base, totalMs) {
        const inv = row.data;
        const t = topologyOf(inv.topology);
        const durMs = inv.done ? inv.durationMillis : this._report.nowMillis - inv.startMillis;
        const leftPct = Math.max(0, (inv.startMillis - base) / totalMs * 100);
        const widthPct = Math.max(0.4, durMs / totalMs * 100);
        const inputs = Object.keys(inv.inputs).length > 0 ? mapToString(inv.inputs) : null;
        const collapsed = this._collapsedRows.has(rowKey);
        return html`
            <tr class="wf-row ${expanded ? 'expanded' : ''}" title="Click to show the full inputs and output"
                @click="${() => this._toggleDetail(rowKey)}">
                <td><div class="wf-agent">
                    ${indents}
                    ${row.depth > 0 ? html`<span class="wf-connector">&#x2514;</span>` : nothing}
                    ${row.hasChildren ? html`<span class="row-toggle ${collapsed ? 'collapsed' : ''}" @click="${e => this._toggleRow(e, rowKey)}">&#9660;</span>` : nothing}
                    <span class="topology-badge ${t.css}">${t.label}</span> ${inv.name}
                    ${inv.iterationIndex >= 0 ? html`<span class="iter-tag">iter ${inv.iterationIndex}</span>` : nothing}
                </div></td>
                <td class="wf-dur">${inv.done ? fmtDur(inv.durationMillis) : html`<em>...</em>`}</td>
                <td class="wf-dur">${inv.done && inv.totalTokenCount > 0 ? fmtTokens(inv.totalTokenCount) : ''}</td>
                <td><div class="wf-bar-track">
                    <div class="wf-bar bar-${t.css}" style="left:${leftPct.toFixed(1)}%;width:${widthPct.toFixed(1)}%;"
                        title="${inv.name}: ${inv.done ? fmtDur(inv.durationMillis) : 'running'}"></div>
                </div></td>
                <td class="wf-io" title="${inputs || ''}">${inputs ? truncate(inputs, 80) : ''}</td>
                <td class="wf-io" title="${inv.done && inv.output != null ? inv.output : ''}">${inv.done && inv.output != null ? truncate(inv.output, 80) : ''}</td>
            </tr>
        `;
    }

    _renderToolRow(tool, indents, rowKey, expanded, base, totalMs) {
        const hasTiming = tool.startMillis != null && tool.durationMillis != null;
        const leftPct = hasTiming ? Math.max(0, (tool.startMillis - base) / totalMs * 100) : 0;
        const widthPct = hasTiming ? Math.max(0.4, tool.durationMillis / totalMs * 100) : 0.4;
        return html`
            <tr class="wf-row tool-row ${expanded ? 'expanded' : ''}" title="Click to show the full request and result"
                @click="${() => this._toggleDetail(rowKey)}">
                <td><div class="wf-agent">
                    ${indents}
                    <span class="wf-connector">&#x2514;</span>
                    <span class="topology-badge tool">Tool</span> ${tool.name}
                    ${tool.failed ? html`<span class="failed-tag">failed</span>` : nothing}
                </div></td>
                <td class="wf-dur">${hasTiming ? fmtDur(tool.durationMillis) : ''}</td>
                <td></td>
                <td><div class="wf-bar-track">
                    ${hasTiming ? html`<div class="wf-bar bar-tool" style="left:${leftPct.toFixed(1)}%;width:${widthPct.toFixed(1)}%;"
                        title="${tool.name}: ${fmtDur(tool.durationMillis)}"></div>` : nothing}
                </div></td>
                <td class="wf-io" title="${tool.arguments || ''}">${tool.arguments ? truncate(tool.arguments, 80) : ''}</td>
                <td class="wf-io" title="${tool.result != null ? tool.result : ''}">${tool.result != null ? truncate(tool.result, 80) : ''}</td>
            </tr>
        `;
    }

    _renderAgentDetail(inv) {
        const inputs = Object.entries(inv.inputs);
        return html`
            <tr class="detail-row"><td colspan="6">
                <dl class="detail">
                    <dt>Agent id</dt><dd><pre>${inv.agentId}</pre></dd>
                    <dt>Started</dt><dd>${inv.startTime}${inv.done ? html` &#8594; ${inv.finishTime} (${fmtDur(inv.durationMillis)})` : ' (running)'}</dd>
                    ${inv.done ? html`<dt>Tokens</dt><dd>${inv.totalTokenCount}</dd>` : nothing}
                    ${inputs.map(([k, v]) => html`<dt>Input ${k}</dt><dd><pre>${v}</pre></dd>`)}
                    ${inv.done ? html`<dt>Output</dt><dd><pre>${inv.output != null ? inv.output : 'null'}</pre></dd>` : nothing}
                </dl>
            </td></tr>
        `;
    }

    _renderToolDetail(tool) {
        return html`
            <tr class="detail-row"><td colspan="6">
                <dl class="detail">
                    <dt>Tool</dt><dd>${tool.name}${tool.failed ? ' (failed)' : ''}</dd>
                    ${tool.durationMillis != null ? html`<dt>Duration</dt><dd>${fmtDur(tool.durationMillis)}</dd>` : nothing}
                    <dt>Request</dt><dd><pre>${tool.arguments}</pre></dd>
                    <dt>Result</dt><dd><pre>${tool.result != null ? tool.result : 'null'}</pre></dd>
                </dl>
            </td></tr>
        `;
    }
}

customElements.define('qwc-agents-executions', QwcAgentsExecutions);
