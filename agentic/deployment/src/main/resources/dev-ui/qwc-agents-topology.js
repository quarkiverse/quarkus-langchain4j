import { LitElement, html, svg, css, nothing } from 'lit';
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

function topologyOf(name) {
    return TOPOLOGY[name] || TOPOLOGY.AI_AGENT;
}

function truncate(text, max) {
    if (text == null) {
        return '';
    }
    return text.length <= max ? text : text.substring(0, max) + '...';
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

export class QwcAgentsTopology extends LitElement {

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
            --c-brand: #2e8555;
            --c-amber: #d97706;
            --fg: var(--lumo-body-text-color);
            --fg2: var(--lumo-secondary-text-color);
            --fg3: var(--lumo-tertiary-text-color);
            --brd: var(--lumo-contrast-10pct);
            --conn: var(--lumo-contrast-30pct);
            --card: var(--lumo-base-color);
            --rad: 8px;
            display: flex;
            flex-direction: column;
            height: 100%;
            color: var(--fg);
            line-height: 1.6;
        }
        *, *::before, *::after { box-sizing: border-box; }
        .toolbar {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 10px 15px;
        }
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

        .legend {
            display: flex; flex-wrap: wrap; gap: 14px; margin-bottom: 18px; align-items: center;
            padding: 10px 16px; background: var(--card); border: 1px solid var(--brd); border-radius: var(--rad);
        }
        .legend-item { display: flex; align-items: center; gap: 5px; font-size: 12px; color: var(--fg2); }
        .legend-dot { width: 12px; height: 12px; border-radius: 3px; }
        .df-legend-title { font-size: 12px; font-weight: 600; color: var(--fg); margin-right: 4px; }
        .df-toggle {
            margin-left: auto; font-size: 11px; padding: 3px 12px; border-radius: 4px; cursor: pointer;
            border: 1px solid var(--brd); background: var(--lumo-contrast-5pct); color: var(--fg2);
        }
        .df-toggle:hover { background: var(--lumo-contrast-10pct); }

        .topology-scroll { overflow-x: auto; padding: 16px 0; }
        .org-tree { text-align: center; position: relative; }
        .org-tree ul { display: flex; justify-content: safe center; padding: 24px 0 0; margin: 0; position: relative; list-style: none; }
        .org-tree > ul { padding-top: 0; }
        .org-tree li { display: flex; flex-direction: column; align-items: center; position: relative; padding: 24px 10px 0; }
        .org-tree > ul > li { padding-top: 0; }
        .org-tree ul::before {
            content: ''; position: absolute; top: 0; left: 50%; border-left: 2px solid var(--conn); height: 24px;
        }
        .org-tree > ul::before { display: none; }
        .org-tree li::before, .org-tree li::after {
            content: ''; position: absolute; top: 0; width: 50%; height: 24px; border-top: 2px solid var(--conn);
        }
        .org-tree li::before { right: 50%; }
        .org-tree li::after { left: 50%; border-left: 2px solid var(--conn); }
        .org-tree > ul > li::before, .org-tree > ul > li::after { display: none; }
        .org-tree li:first-child::before { border-top: none; }
        .org-tree li:last-child::after { border-top: none; }
        .org-tree li:only-child::before { display: none; }
        .org-tree li:only-child::after { border-top: none; }

        .node-card {
            position: relative; background: var(--card); border: 1px solid var(--brd); border-radius: var(--rad);
            padding: 10px 14px; min-width: 140px; max-width: 260px; text-align: left;
            box-shadow: 0 1px 3px rgba(0, 0, 0, .06); transition: box-shadow .2s, transform .2s;
        }
        .node-card:hover { box-shadow: 0 4px 14px rgba(0, 0, 0, .15); transform: translateY(-2px); }
        .node-card-header { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; flex-wrap: wrap; }
        .agent-name { font-size: 14px; font-weight: 600; }
        .node-border-ai { border-left: 3px solid var(--c-ai); }
        .node-border-nonai { border-left: 3px solid var(--c-nonai); }
        .node-border-human { border-left: 3px solid var(--c-human); }
        .node-border-seq { border-left: 3px solid var(--c-seq); }
        .node-border-par { border-left: 3px solid var(--c-par); }
        .node-border-loop { border-left: 3px solid var(--c-loop); }
        .node-border-rtr { border-left: 3px solid var(--c-rtr); }
        .node-border-star { border-left: 3px solid var(--c-star); }

        .topology-badge {
            display: inline-block; padding: 1px 7px; border-radius: 4px; font-size: 10px; font-weight: 700;
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

        .async-badge, .condition-label {
            font-size: 9px; padding: 0 5px; border-radius: 3px;
            background: color-mix(in srgb, var(--c-amber) 16%, var(--card));
            color: color-mix(in srgb, var(--c-amber) 70%, var(--fg));
            border: 1px solid color-mix(in srgb, var(--c-amber) 55%, var(--card));
        }
        .condition-label { padding: 1px 8px; margin-bottom: 4px; white-space: nowrap; }

        .node-details { font-size: 11px; }
        .node-detail { display: flex; gap: 4px; margin-top: 1px; }
        .nd-label { color: var(--fg3); font-weight: 500; }
        .nd-value { color: var(--fg2); }

        .loop-info { display: flex; gap: 6px; margin-top: 4px; flex-wrap: wrap; }
        .loop-tag {
            font-size: 10px; padding: 0 6px; border-radius: 3px;
            background: color-mix(in srgb, var(--c-loop) 14%, var(--card));
            color: color-mix(in srgb, var(--c-loop) 75%, var(--fg));
            border: 1px solid color-mix(in srgb, var(--c-loop) 45%, var(--card));
        }

        .key-flow {
            display: flex; flex-wrap: wrap; gap: 3px; margin-top: 5px; padding-top: 5px;
            border-top: 1px dashed var(--brd);
        }
        .key-pill {
            font-size: 9px; font-weight: 600; padding: 0 6px; border-radius: 3px; white-space: nowrap;
            border: 1px solid var(--kc); background: var(--card);
            color: color-mix(in srgb, var(--kc) 75%, var(--fg));
        }

        .df-svg { position: absolute; top: 0; left: 0; pointer-events: none; z-index: 2; overflow: visible; }
        .df-svg.df-hidden { display: none; }
        .footer { text-align: center; padding: 16px 0 4px; font-size: 12px; color: var(--fg3); border-top: 1px solid var(--brd); margin-top: 20px; }
    `;

    static properties = {
        _entries: { state: true },
        _selectedIndex: { state: true },
        _topology: { state: true },
        _loading: { state: true },
        _error: { state: true },
        _edgesHidden: { state: true },
        _loadedAt: { state: true },
    };

    jsonRpc = new JsonRpc(this);

    constructor() {
        super();
        this._entries = [];
        this._selectedIndex = 0;
        this._topology = null;
        this._loading = true;
        this._error = null;
        this._edgesHidden = false;
        this._loadedAt = null;
        this._resizeObserver = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._resizeObserver = new ResizeObserver(() => this._drawDataFlow());
        this._resizeObserver.observe(this);
        this._loadEntries();
    }

    disconnectedCallback() {
        if (this._resizeObserver) {
            this._resizeObserver.disconnect();
        }
        super.disconnectedCallback();
    }

    updated() {
        this._drawDataFlow();
    }

    _loadEntries() {
        this.jsonRpc.getRootAgentEntries()
            .then(response => {
                this._entries = response.result || [];
                this._selectedIndex = this._entries.length > 0 ? this._entries[0].index : 0;
                this._loadTopology();
            })
            .catch(error => {
                this._error = String(error);
                this._loading = false;
            });
    }

    _loadTopology() {
        this._loading = true;
        this._error = null;
        this.jsonRpc.getTopology({ index: this._selectedIndex })
            .then(response => {
                const result = response.result;
                if (result.error) {
                    this._error = result.error;
                    this._topology = null;
                } else {
                    this._topology = result;
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
            this._loadTopology();
        }
    }

    _openReport() {
        openHtmlReport(this.jsonRpc.getTopologyReportHtml({ index: this._selectedIndex }));
    }

    render() {
        return html`
            ${this._renderToolbar()}
            <div class="content">
                ${this._loading ? html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`
                    : this._error ? html`<div class="placeholder">${this._error}</div>`
                    : this._renderTopology()}
            </div>
        `;
    }

    _renderToolbar() {
        const items = this._entries.map(e => ({ label: e.typeName, value: String(e.index) }));
        return html`
            <div class="toolbar">
                ${items.length > 1 ? html`
                    <vaadin-select label="Root agent" .items="${items}" .value="${String(this._selectedIndex)}"
                        @value-changed="${this._onRootSelected}"></vaadin-select>
                ` : items.length === 1 ? html`<span class="root-name">${items[0].label}</span>` : nothing}
                <vaadin-button theme="small" @click="${() => this._loadTopology()}">Refresh</vaadin-button>
                <span class="spacer"></span>
                <vaadin-button theme="small tertiary" title="Open the report generated by LangChain4j in a new tab"
                    ?disabled="${this._entries.length === 0}" @click="${() => this._openReport()}">
                    <vaadin-icon icon="font-awesome-solid:up-right-from-square" slot="prefix"></vaadin-icon>
                    Open LangChain4j report
                </vaadin-button>
            </div>
        `;
    }

    _renderTopology() {
        const topology = this._topology;
        const keyColors = {};
        for (const entry of topology.dataFlow) {
            keyColors[entry.key] = entry.color;
        }
        return html`
            <section>
                <div class="section-header">
                    <div class="section-icon">
                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                            <circle cx="8" cy="3" r="2" fill="white"/><circle cx="4" cy="13" r="2" fill="white"/>
                            <circle cx="12" cy="13" r="2" fill="white"/>
                            <line x1="8" y1="5" x2="4" y2="11" stroke="white" stroke-width="1.5"/>
                            <line x1="8" y1="5" x2="12" y2="11" stroke="white" stroke-width="1.5"/>
                        </svg>
                    </div>
                    <h2 class="section-title">System Topology</h2>
                </div>
                <div class="legend">
                    ${topology.legend.map(t => html`
                        <div class="legend-item">
                            <div class="legend-dot" style="background:var(--c-${topologyOf(t).css})"></div>
                            <span>${topologyOf(t).label}</span>
                        </div>`)}
                </div>
                ${topology.dataFlow.length > 0 ? html`
                    <div class="legend df-legend">
                        <span class="df-legend-title">Data Flow</span>
                        ${topology.dataFlow.map(e => html`
                            <div class="legend-item">
                                <div class="legend-dot" style="background:${e.color}"></div>
                                <span>${e.key}</span>
                            </div>`)}
                        <button class="df-toggle" @click="${() => { this._edgesHidden = !this._edgesHidden; }}">Toggle Edges</button>
                    </div>` : nothing}
                <div class="topology-scroll">
                    <div class="org-tree">
                        <svg class="df-svg ${this._edgesHidden ? 'df-hidden' : ''}">
                            <defs>
                                ${topology.dataFlow.map(e => svg`
                                    <marker id="${this._markerId(e.key)}" viewBox="0 0 10 10" refX="10" refY="5"
                                        markerWidth="5" markerHeight="5" orient="auto">
                                        <path d="M0 0 L10 5 L0 10 z" fill="${e.color}"></path>
                                    </marker>`)}
                            </defs>
                        </svg>
                        <ul>${this._renderNode(topology.root, keyColors)}</ul>
                    </div>
                </div>
                <div class="footer">Loaded from the running application at ${this._loadedAt ? this._loadedAt.toLocaleTimeString() : ''}</div>
            </section>
        `;
    }

    _renderNode(node, keyColors) {
        const t = topologyOf(node.topology);
        const inputKeys = node.arguments.map(a => a.name).join(',');
        const showType = node.typeName && node.typeName !== node.name;
        return html`
            <li>
                ${node.condition ? html`<div class="condition-label">when: ${node.condition}</div>` : nothing}
                <div class="node-card node-border-${t.css}"
                    title="${node.agentId}${node.className ? '\n' + node.className : ''}"
                    data-agent-id="${node.agentId}"
                    data-output-key="${node.outputKey || ''}"
                    data-output-color="${node.outputKey ? (keyColors[node.outputKey] || '#999') : ''}"
                    data-input-keys="${inputKeys}">
                    <div class="node-card-header">
                        <span class="topology-badge ${t.css}">${t.label}</span>
                        <span class="agent-name">${node.name}</span>
                        ${node.async ? html`<span class="async-badge">async</span>` : nothing}
                    </div>
                    <div class="node-details">
                        ${showType ? this._detail('Type', node.typeName) : nothing}
                        ${node.description ? this._detail('Desc', truncate(node.description, 60), node.description) : nothing}
                        ${node.outputType ? this._detail('Returns', node.outputType) : nothing}
                        ${node.plannerType ? this._detail('Planner', node.plannerType) : nothing}
                        ${node.loop ? html`
                            <div class="loop-info">
                                <span class="loop-tag">max ${node.loop.maxIterations}</span>
                                ${node.loop.exitCondition ? html`<span class="loop-tag" title="${node.loop.exitCondition}">exit: ${truncate(node.loop.exitCondition, 40)}</span>` : nothing}
                                <span class="loop-tag">${node.loop.testExitAtLoopEnd ? 'test at end' : 'test at start'}</span>
                            </div>` : nothing}
                    </div>
                    ${node.arguments.length > 0 || node.outputKey ? html`
                        <div class="key-flow">
                            ${node.arguments.map(a => html`<span class="key-pill key-in" style="--kc:${keyColors[a.name] || '#999'}" title="${a.type || ''}">&#8592; ${a.name}</span>`)}
                            ${node.outputKey ? html`<span class="key-pill key-out" style="--kc:${keyColors[node.outputKey] || '#999'}">&#8594; ${node.outputKey}</span>` : nothing}
                        </div>` : nothing}
                </div>
                ${node.subagents.length > 0 ? html`<ul>${node.subagents.map(child => this._renderNode(child, keyColors))}</ul>` : nothing}
            </li>
        `;
    }

    _detail(label, value, fullValue) {
        return html`<div class="node-detail" title="${fullValue || value}"><span class="nd-label">${label}</span> <span class="nd-value">${value}</span></div>`;
    }

    _markerId(key) {
        return 'ah-' + Math.abs(this._hashCode(key));
    }

    _hashCode(s) {
        let h = 0;
        for (let i = 0; i < s.length; i++) {
            h = ((h << 5) - h) + s.charCodeAt(i);
            h |= 0;
        }
        return h;
    }

    _drawDataFlow() {
        const svg = this.renderRoot.querySelector('.df-svg');
        const tree = svg ? svg.parentElement : null;
        if (!svg || !tree) {
            return;
        }
        svg.style.width = tree.scrollWidth + 'px';
        svg.style.height = tree.scrollHeight + 'px';
        svg.querySelectorAll('.df-path').forEach(p => p.remove());

        const treeRect = tree.getBoundingClientRect();
        const producers = {};
        tree.querySelectorAll('.node-card[data-output-key]').forEach(n => {
            const key = n.getAttribute('data-output-key');
            if (key) {
                (producers[key] = producers[key] || []).push(n);
            }
        });
        tree.querySelectorAll('.node-card[data-input-keys]').forEach(consumer => {
            const keys = consumer.getAttribute('data-input-keys');
            if (!keys) {
                return;
            }
            const cRect = consumer.getBoundingClientRect();
            const cx = cRect.left + cRect.width / 2 - treeRect.left;
            const cy = cRect.top - treeRect.top;
            keys.split(',').forEach(key => {
                (producers[key] || []).forEach(producer => {
                    if (producer === consumer || producer.contains(consumer) || consumer.contains(producer)) {
                        return;
                    }
                    const pRect = producer.getBoundingClientRect();
                    const px = pRect.left + pRect.width / 2 - treeRect.left;
                    const py = pRect.bottom - treeRect.top;
                    this._drawEdge(svg, px, py, cx, cy, producer.getAttribute('data-output-color') || '#999', this._markerId(key));
                });
            });
        });
    }

    _drawEdge(svg, x1, y1, x2, y2, color, markerId) {
        const dx = x2 - x1;
        const dy = y2 - y1;
        const dist = Math.sqrt(dx * dx + dy * dy);
        const bend = Math.min(60, dist * 0.3);
        const cpx = (x1 + x2) / 2 + (-dy / (dist || 1) * bend);
        const cpy = (y1 + y2) / 2 + (dx / (dist || 1) * bend);
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('d', 'M' + x1 + ',' + y1 + ' Q' + cpx + ',' + cpy + ' ' + x2 + ',' + y2);
        path.setAttribute('stroke', color);
        path.setAttribute('stroke-width', '1.8');
        path.setAttribute('stroke-dasharray', '6,4');
        path.setAttribute('fill', 'none');
        path.setAttribute('opacity', '0.55');
        path.setAttribute('class', 'df-path');
        path.setAttribute('marker-end', 'url(#' + markerId + ')');
        svg.appendChild(path);
    }
}

customElements.define('qwc-agents-topology', QwcAgentsTopology);
