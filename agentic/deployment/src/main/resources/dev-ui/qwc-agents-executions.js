import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';
import '@vaadin/select';
import '@vaadin/progress-bar';

const TYPE_COLORS = {
    'Agent': '#2e8555',
    'SequenceAgent': '#0891b2',
    'ParallelAgent': '#3b82f6',
    'ParallelMapperAgent': '#3b82f6',
    'LoopAgent': '#7c3aed',
    'ConditionalAgent': '#dc2626',
    'SupervisorAgent': '#ca8a04',
    'PlannerAgent': '#6b7280',
    'HumanInTheLoop': '#d97706',
    'A2AClientAgent': '#6b7280',
};

export class QwcAgentsExecutions extends LitElement {

    static styles = css`
        :host {
            height: 100%;
            display: flex;
            flex-direction: column;
        }
        .toolbar {
            padding: 10px 15px;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .executions-container {
            flex: 1;
            padding: 0 15px 15px 15px;
            overflow: auto;
        }
        .single-exec {
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: 4px;
            margin-bottom: 12px;
            padding: 10px 12px;
        }
        .exec-summary {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 8px;
        }
        .status-dot {
            width: 9px;
            height: 9px;
            border-radius: 50%;
            display: inline-block;
        }
        .st-ok { background: var(--lumo-success-color); }
        .st-fail { background: var(--lumo-error-color); }
        .st-run { background: var(--lumo-primary-color); }
        .exec-agent { font-weight: 600; }
        .dur-badge {
            font-size: 0.75em;
            background: var(--lumo-contrast-10pct);
            border-radius: 4px;
            padding: 1px 6px;
        }
        .exec-time {
            font-size: 0.8em;
            color: var(--lumo-secondary-text-color);
        }
        .error-box {
            background: var(--lumo-error-color-10pct);
            color: var(--lumo-error-text-color);
            border-radius: 4px;
            padding: 6px 8px;
            font-size: 0.85em;
            margin-bottom: 8px;
        }
        table.wf-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.85em;
        }
        .wf-table th {
            text-align: left;
            font-weight: 500;
            color: var(--lumo-secondary-text-color);
            border-bottom: 1px solid var(--lumo-contrast-10pct);
            padding: 4px 8px;
        }
        .wf-table td {
            padding: 4px 8px;
            border-bottom: 1px solid var(--lumo-contrast-5pct);
            vertical-align: top;
        }
        .wf-timeline-col { width: 30%; }
        .wf-agent {
            display: flex;
            align-items: center;
            gap: 4px;
            white-space: nowrap;
        }
        .wf-indent {
            display: inline-block;
            width: 14px;
        }
        .wf-connector { color: var(--lumo-contrast-40pct); }
        .type-badge {
            font-size: 0.7em;
            color: #fff;
            border-radius: 3px;
            padding: 0 5px;
        }
        .iter-tag {
            font-size: 0.7em;
            background: var(--lumo-contrast-10pct);
            border-radius: 3px;
            padding: 0 4px;
        }
        .fail-tag {
            font-size: 0.7em;
            background: var(--lumo-error-color-10pct);
            color: var(--lumo-error-text-color);
            border-radius: 3px;
            padding: 0 4px;
        }
        .wf-num {
            font-variant-numeric: tabular-nums;
            color: var(--lumo-secondary-text-color);
        }
        .wf-bar-track {
            position: relative;
            height: 12px;
            background: var(--lumo-contrast-5pct);
            border-radius: 3px;
        }
        .wf-bar {
            position: absolute;
            height: 12px;
            border-radius: 3px;
            min-width: 2px;
        }
        .wf-io {
            max-width: 220px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            color: var(--lumo-secondary-text-color);
        }
        .placeholder {
            padding: 20px;
            text-align: center;
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        _executions: { state: true },
        _loading: { state: true },
        _error: { state: true },
        _agentEntries: { state: true },
        _selectedIndex: { state: true },
    };

    jsonRpc = new JsonRpc(this);

    constructor() {
        super();
        this._executions = [];
        this._loading = true;
        this._error = null;
        this._agentEntries = [];
        this._selectedIndex = 0;
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadAgentEntries();
    }

    _loadAgentEntries() {
        this.jsonRpc.getRootAgentEntries()
            .then(response => {
                this._agentEntries = response.result || [];
                this._selectedIndex = this._agentEntries.length > 0 ? this._agentEntries[0].index : 0;
                this._loadReport();
            })
            .catch(() => this._loadReport());
    }

    _onAgentSelected(e) {
        this._selectedIndex = parseInt(e.target.value, 10);
        this._loadReport();
    }

    _loadReport() {
        this._loading = true;
        this._error = null;
        this.jsonRpc.getExecutionReportJson({ index: this._selectedIndex })
            .then(response => {
                const result = response.result;
                if (result && result.error) {
                    this._error = result.error;
                    this._executions = [];
                } else {
                    this._executions = (result && result.executions) || [];
                }
                this._loading = false;
            })
            .catch(error => {
                this._error = String(error);
                this._loading = false;
            });
    }

    render() {
        const agentItems = this._agentEntries.map(e => ({
            label: e.name,
            value: String(e.index),
        }));

        return html`
            <div class="toolbar">
                ${agentItems.length > 1 ? html`
                    <vaadin-select
                        label="Root Agent"
                        .items="${agentItems}"
                        .value="${String(this._selectedIndex)}"
                        @value-changed="${this._onAgentSelected}">
                    </vaadin-select>
                ` : ''}
                <vaadin-button theme="small" @click="${() => this._loadReport()}">
                    Refresh
                </vaadin-button>
                ${this._loading ? html`<span>Loading execution report...</span>` : ''}
            </div>
            ${this._loading ? html`
                <vaadin-progress-bar indeterminate></vaadin-progress-bar>
            ` : this._error ? html`
                <div class="placeholder">${this._error}</div>
            ` : this._executions.length === 0 ? html`
                <div class="placeholder">No execution data available. Invoke an agent first.</div>
            ` : html`
                <div class="executions-container">
                    ${this._groupByRun().map(run => this._renderRun(run))}
                </div>
            `}
        `;
    }

    _groupByRun() {
        const runs = new Map();
        for (const exec of this._executions) {
            if (!runs.has(exec.memoryId)) {
                runs.set(exec.memoryId, { memoryId: exec.memoryId, execs: [] });
            }
            runs.get(exec.memoryId).execs.push(exec);
        }
        return [...runs.values()];
    }

    _renderRun(run) {
        const tops = run.execs.map(e => e.topLevel).filter(Boolean);
        const base = Math.min(...tops.map(t => t.startMillis));
        const end = Math.max(...tops.map(t => t.startMillis + (t.duration ?? (Date.now() - t.startMillis))));
        const total = Math.max(1, end - base);
        const failed = run.execs.some(e => e.status === 'failed');
        const ongoing = run.execs.some(e => e.status === 'ongoing');
        const statusClass = failed ? 'st-fail' : ongoing ? 'st-run' : 'st-ok';
        const firstError = run.execs.find(e => e.error);
        const workflow = this._agentEntries.find(e => String(e.index) === String(this._selectedIndex));

        const workflowInvocation = {
            agentName: workflow ? workflow.name : 'Workflow',
            type: workflow ? workflow.type : 'SequenceAgent',
            startMillis: base,
            duration: ongoing ? undefined : end - base,
            tokenCount: tops.reduce((sum, t) => sum + (t.tokenCount ?? 0), 0),
            output: tops.length > 0 ? tops[tops.length - 1].output : '',
            nestedInvocations: tops,
            toolExecutions: [],
        };

        return html`
            <div class="single-exec">
                <div class="exec-summary">
                    <span class="status-dot ${statusClass}"></span>
                    <span class="exec-agent">${workflowInvocation.agentName}</span>
                    <span class="dur-badge">${ongoing ? 'running...' : this._fmtDur(end - base)}</span>
                    <span class="exec-time">memoryId: ${run.memoryId}</span>
                </div>
                ${firstError ? html`<div class="error-box"><strong>Error:</strong> ${firstError.error}</div>` : ''}
                <table class="wf-table">
                    <thead>
                        <tr>
                            <th>Agent</th><th>Duration</th><th>Tokens</th>
                            <th class="wf-timeline-col">Timeline</th><th>Input</th><th>Output</th>
                        </tr>
                    </thead>
                    <tbody>${this._renderRows(workflowInvocation, 0, base, total)}</tbody>
                </table>
            </div>
        `;
    }

    _renderRows(inv, depth, base, total) {
        const color = TYPE_COLORS[inv.type] || TYPE_COLORS['Agent'];
        const durMs = inv.duration !== undefined ? inv.duration : (Date.now() - inv.startMillis);
        const leftPct = Math.max(0, (inv.startMillis - base) / total * 100);
        const widthPct = Math.max(0.4, durMs / total * 100);
        const inputStr = this._fmtMap(inv.inputs);

        const rows = [html`
            <tr>
                <td>${this._agentCell(depth, html`
                    <span class="type-badge" style="background:${color}">${inv.type}</span>
                    <span>${inv.agentName}</span>
                    ${inv.iterationIndex !== undefined ? html`<span class="iter-tag">iter ${inv.iterationIndex}</span>` : ''}
                `)}</td>
                <td class="wf-num">${inv.duration !== undefined ? this._fmtDur(inv.duration) : html`<em>...</em>`}</td>
                <td class="wf-num">${inv.duration !== undefined && inv.tokenCount > 0 ? this._fmtTokens(inv.tokenCount) : ''}</td>
                <td>${this._bar(leftPct, widthPct, color, `${inv.agentName}: ${inv.duration !== undefined ? this._fmtDur(inv.duration) : 'running'}`)}</td>
                <td class="wf-io" title="${inputStr}">${inputStr}</td>
                <td class="wf-io" title="${inv.output || ''}">${inv.output || ''}</td>
            </tr>
        `];

        for (const tool of (inv.toolExecutions || [])) {
            rows.push(this._renderToolRow(tool, depth + 1, base, total));
        }
        for (const nested of (inv.nestedInvocations || [])) {
            rows.push(...this._renderRows(nested, depth + 1, base, total));
        }
        return rows;
    }

    _renderToolRow(tool, depth, base, total) {
        const hasTiming = tool.startMillis !== undefined && tool.duration !== undefined;
        const leftPct = hasTiming ? Math.max(0, (tool.startMillis - base) / total * 100) : 0;
        const widthPct = hasTiming ? Math.max(0.4, tool.duration / total * 100) : 0;

        return html`
            <tr>
                <td>${this._agentCell(depth, html`
                    <span class="type-badge" style="background:var(--lumo-contrast-50pct)">Tool</span>
                    <span>${tool.name}</span>
                    ${tool.failed ? html`<span class="fail-tag">failed</span>` : ''}
                `)}</td>
                <td class="wf-num">${hasTiming ? this._fmtDur(tool.duration) : ''}</td>
                <td></td>
                <td>${hasTiming ? this._bar(leftPct, widthPct, 'var(--lumo-contrast-50pct)', `${tool.name}: ${this._fmtDur(tool.duration)}`) : ''}</td>
                <td class="wf-io" title="${tool.arguments || ''}">${tool.arguments || ''}</td>
                <td class="wf-io" title="${tool.result || ''}">${tool.result || ''}</td>
            </tr>
        `;
    }

    _agentCell(depth, content) {
        const indents = [];
        for (let i = 0; i < depth; i++) {
            indents.push(html`<span class="wf-indent"></span>`);
        }
        return html`<div class="wf-agent">
            ${indents}
            ${depth > 0 ? html`<span class="wf-connector">&#x2514;</span>` : ''}
            ${content}
        </div>`;
    }

    _bar(leftPct, widthPct, color, title) {
        return html`<div class="wf-bar-track">
            <div class="wf-bar" style="left:${leftPct.toFixed(1)}%;width:${widthPct.toFixed(1)}%;background:${color}" title="${title}"></div>
        </div>`;
    }

    _fmtMap(map) {
        if (!map) {
            return '';
        }
        return Object.entries(map).map(([k, v]) => `${k}=${v}`).join(', ');
    }

    _fmtDur(ms) {
        if (ms < 1000) {
            return `${ms}ms`;
        }
        if (ms < 60000) {
            return `${(ms / 1000).toFixed(1)}s`;
        }
        return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`;
    }

    _fmtTokens(tokens) {
        return tokens < 1000 ? String(tokens) : `${(tokens / 1000).toFixed(1)}k`;
    }
}

customElements.define('qwc-agents-executions', QwcAgentsExecutions);
