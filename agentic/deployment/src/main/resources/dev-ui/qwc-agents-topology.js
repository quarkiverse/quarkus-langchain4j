import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import { themeState } from 'theme-state';
import 'echarts/dist/echarts.min.js';
import '@vaadin/button';
import '@vaadin/select';
import '@vaadin/progress-bar';

export class QwcAgentsTopology extends LitElement {

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
        .diagram-container {
            flex: 1;
            padding: 0 15px 15px 15px;
            overflow: hidden;
        }
        .chart {
            width: 100%;
            height: 100%;
        }
        .placeholder {
            padding: 20px;
            text-align: center;
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        _graph: { state: true },
        _loading: { state: true },
        _error: { state: true },
        _agentEntries: { state: true },
        _selectedIndex: { state: true },
    };

    jsonRpc = new JsonRpc(this);

    constructor() {
        super();
        this._graph = null;
        this._loading = true;
        this._error = null;
        this._agentEntries = [];
        this._selectedIndex = 0;
        this._chart = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._resizeHandler = () => this._chart?.resize();
        window.addEventListener('resize', this._resizeHandler);
        this._themeObserver = () => this._renderChart();
        themeState.addObserver(this._themeObserver);
        this._loadAgentEntries();
    }

    disconnectedCallback() {
        window.removeEventListener('resize', this._resizeHandler);
        themeState.removeObserver(this._themeObserver);
        this._chart?.dispose();
        this._chart = null;
        super.disconnectedCallback();
    }

    _loadAgentEntries() {
        this.jsonRpc.getRootAgentEntries()
            .then(response => {
                this._agentEntries = response.result || [];
                this._selectedIndex = this._agentEntries.length > 0 ? this._agentEntries[0].index : 0;
                this._loadTopology();
            })
            .catch(() => this._loadTopology());
    }

    _onAgentSelected(e) {
        this._selectedIndex = parseInt(e.target.value, 10);
        this._loadTopology();
    }

    _loadTopology() {
        this._loading = true;
        this._error = null;
        this.jsonRpc.getTopologyGraph({ index: this._selectedIndex })
            .then(response => {
                const result = response.result;
                if (result && result.error) {
                    this._error = result.error;
                    this._graph = null;
                } else {
                    this._graph = result;
                }
                this._loading = false;
            })
            .catch(error => {
                this._error = String(error);
                this._loading = false;
            });
    }

    updated(changed) {
        super.updated?.(changed);
        this._renderChart();
    }

    _renderChart() {
        const container = this.shadowRoot?.querySelector('.chart');
        if (!container || !this._graph || !this._graph.nodes) {
            return;
        }
        if (this._chart && this._chart.getDom() !== container) {
            this._chart.dispose();
            this._chart = null;
        }
        if (!this._chart) {
            this._chart = echarts.init(container);
        }
        this._chart.setOption(this._buildOption(), true);
        this._chart.resize();
    }

    _buildOption() {
        const style = getComputedStyle(this);
        const textColor = style.getPropertyValue('--lumo-body-text-color');
        const lineColor = style.getPropertyValue('--lumo-contrast-50pct');

        const nodes = this._graph.nodes.map(node => ({
            id: node.id,
            name: node.name,
            kind: node.kind,
            x: node.x,
            y: node.y,
            symbol: 'roundRect',
            symbolSize: [150, 48],
            itemStyle: { color: node.color },
            label: {
                show: true,
                formatter: `{kind|${node.kind}}\n{name|${node.name}}`,
                rich: {
                    kind: { fontSize: 10, color: '#ffffff', opacity: 0.85 },
                    name: { fontSize: 12, fontWeight: 'bold', color: '#ffffff', padding: [4, 0, 0, 0] },
                },
            },
        }));

        const links = this._graph.links.map(link => {
            return {
                source: link.source,
                target: link.target,
                lineStyle: this._linkStyle(link.kind, lineColor),
                label: {
                    show: !!link.label,
                    formatter: link.label ?? '',
                    fontSize: 10,
                    color: textColor,
                },
            };
        });

        return {
            animation: false,
            tooltip: {
                formatter: params => params.dataType === 'node'
                    ? `${params.data.kind}<br/><b>${params.data.name}</b>`
                    : '',
            },
            series: [{
                type: 'graph',
                layout: 'none',
                roam: true,
                data: nodes,
                edges: links,
                edgeSymbol: ['none', 'arrow'],
                edgeSymbolSize: 9,
            }],
        };
    }

    _linkStyle(kind, lineColor) {
        switch (kind) {
            case 'state':
                return { type: 'dashed', color: lineColor, opacity: 0.6, curveness: 0.25 };
            case 'loop':
                return { type: 'dashed', color: lineColor, curveness: -0.45 };
            case 'branch':
                return { type: 'dashed', color: lineColor, curveness: 0.1 };
            case 'star':
                return { type: 'solid', color: lineColor, curveness: 0.1 };
            default:
                return { type: 'solid', color: lineColor, curveness: 0 };
        }
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
                <vaadin-button theme="small" @click="${() => this._loadTopology()}">
                    Refresh
                </vaadin-button>
                ${this._loading ? html`<span>Loading topology...</span>` : ''}
            </div>
            ${this._loading ? html`
                <vaadin-progress-bar indeterminate></vaadin-progress-bar>
            ` : this._error ? html`
                <div class="placeholder">${this._error}</div>
            ` : this._graph ? html`
                <div class="diagram-container">
                    <div class="chart"></div>
                </div>
            ` : html`
                <div class="placeholder">No topology available.</div>
            `}
        `;
    }
}

customElements.define('qwc-agents-topology', QwcAgentsTopology);
