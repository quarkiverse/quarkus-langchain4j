import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';
import '@vaadin/text-field';
import '@vaadin/number-field';
import '@vaadin/checkbox';
import '@vaadin/text-area';
import '@vaadin/button';
import '@vaadin/dialog';
import '@vaadin/vertical-layout';
import '@vaadin/icon';
import '@vaadin/progress-bar';
import { dialogHeaderRenderer, dialogRenderer } from '@vaadin/dialog/lit.js';
import 'qui-themed-code-block';
import '@qomponent/qui-badge';
import { JsonRpc } from 'jsonrpc';

/**
 * This component shows the MCP clients and their tools.
 */
export class QwcMcpClients extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
       :host {
          display: flex;
          flex-direction: column;
          gap: 10px;
        }
        .grid {
          display: flex;
          flex-direction: column;
          padding-left: 5px;
          padding-right: 5px;
          max-width: 100%;
        }
        code {
          font-size: 85%;
        }
        .filterText {
          width: 100%;
        }
        `;

    static properties = {
        _clientInfos: { state: true },
        _allTools: { state: true },
        _filtered: { state: true, type: Array },
        _selectedTool: { state: true },
        _showInputDialog: { state: true, type: Boolean },
        _showResultDialog: { state: true, type: Boolean },
        _inputValues: { state: true },
        _toolResult: { state: true },
        _searchTerm: { state: true }
    };

    constructor() {
        super();
        this._selectedTool = null;
        this._showInputDialog = false;
        this._showResultDialog = false;
        this._inputValues = new Map();
        this._toolResult = null;
        this._clientInfos = null;
        this._allTools = null;
        this._filtered = null;
        this._searchTerm = '';
    }

    connectedCallback() {
        super.connectedCallback();
        this._loadClients();
        this._inputValues.clear();
    }

    render() {
        if (this._allTools) {
            return html`${this._renderResultDialog()}
                        ${this._renderInputDialog()}
                        ${this._renderGrid()}`;
        } else {
            return html`
            <div style="color: var(--lumo-secondary-text-color);width: 95%;">
                <div>Fetching MCP clients...</div>
                <vaadin-progress-bar indeterminate></vaadin-progress-bar>
            </div>
            `;
        }
    }

    _renderGrid() {
        return html`<div class="grid">
                    ${this._renderFilterTextbar()}
                    <vaadin-grid .items="${this._filtered}" theme="row-stripes no-border" all-rows-visible
                        @active-item-changed="${(e) => {
                            const item = e.detail.value;
                            if (item) {
                                this._selectedTool = item;
                                this._showInputDialog = true;
                            }
                        }}">
                        <vaadin-grid-sort-column
                            header="Name"
                            path="name"
                            auto-width
                            ${columnBodyRenderer(this._renderName, [])}
                        ></vaadin-grid-sort-column>
                        <vaadin-grid-sort-column
                            header="MCP Client"
                            path="clientName"
                            auto-width
                            ${columnBodyRenderer(this._renderClientName, [])}
                        ></vaadin-grid-sort-column>
                        <vaadin-grid-sort-column
                            header="Description"
                            path="description"
                            auto-width>
                        </vaadin-grid-sort-column>
                        <vaadin-grid-column
                            header="Args"
                            frozen-to-end
                            auto-width
                            flex-grow="0"
                            ${columnBodyRenderer(this._renderArgsCount, [])}
                        ></vaadin-grid-column>
                    </vaadin-grid>
                </div>`;
    }

    _renderFilterTextbar() {
        return html`<vaadin-text-field
                class="filterText"
                placeholder="Filter"
                @value-changed="${(e) => {
                    this._searchTerm = (e.detail.value || '').trim().toLowerCase();
                    this._applyFilter();
                }}">
            <vaadin-icon slot="prefix" icon="font-awesome-solid:filter"></vaadin-icon>
            <qui-badge slot="suffix"><span>${this._filtered?.length ?? 0}</span></qui-badge>
        </vaadin-text-field>`;
    }

    _renderResultDialog() {
        return html`<vaadin-dialog
            header-title="Tool execution result"
            resizable
            draggable
            .opened="${this._showResultDialog}"
            @opened-changed="${(e) => {
                this._showResultDialog = e.detail.value;
                if (!this._showResultDialog) {
                    this._toolResult = null;
                }
            }}"
            ${dialogHeaderRenderer(() => html`
                <vaadin-button theme="tertiary" @click="${() => { this._showResultDialog = false; }}">
                    <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                </vaadin-button>
            `, [])}
            ${dialogRenderer(() => html`
                <vaadin-vertical-layout style="width:80vw; max-width:800px; height:auto; max-height:80vh; overflow:auto;">
                    <qui-themed-code-block mode='json' content='${this._toolResult}' style="width:100%;"></qui-themed-code-block>
                </vaadin-vertical-layout>
            `, [])}
        ></vaadin-dialog>`;
    }

    _renderInputDialog() {
        if (!this._selectedTool) return html``;
        const tool = this._selectedTool;
        const args = tool.args || [];

        return html`<vaadin-dialog
            header-title="Execute tool: ${tool.name}"
            resizable
            draggable
            .opened="${this._showInputDialog}"
            @opened-changed="${(e) => {
                this._showInputDialog = e.detail.value;
                if (!this._showInputDialog) {
                    this._selectedTool = null;
                    this._inputValues.clear();
                }
            }}"
            ${dialogHeaderRenderer(() => html`
                <vaadin-button theme="tertiary" @click="${() => { this._showInputDialog = false; }}">
                    <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                </vaadin-button>
            `, [])}
            ${dialogRenderer(() => html`
                <vaadin-vertical-layout theme="spacing" style="width:500px; max-width:80vw;">
                    ${args.length > 0
                        ? args.map((arg) => this._renderArgInput(tool.name, arg))
                        : html`<p style="color: var(--lumo-secondary-text-color);">This tool has no arguments.</p>`
                    }
                    <vaadin-button theme="primary" @click="${() => this._executeTool()}">
                        Execute
                    </vaadin-button>
                </vaadin-vertical-layout>
            `, [])}
        ></vaadin-dialog>`;
    }

    _renderArgInput(toolName, arg) {
        const argType = (arg.type || '').toLowerCase();

        if (argType === 'boolean') {
            return html`
                <vaadin-checkbox
                    label="${arg.name}${arg.required ? ' *' : ''}"
                    helper-text="${arg.description || ''}"
                    @change=${(e) => this._updateInputValueTyped(toolName, arg.name, e.target.checked, 'boolean')}>
                </vaadin-checkbox>
            `;
        } else if (argType === 'integer' || argType === 'number') {
            return html`
                <vaadin-number-field
                    label="${arg.name}"
                    helper-text="${arg.description || ''}"
                    placeholder="${arg.type || ''}"
                    ?required="${arg.required}"
                    ?step-buttons-visible="${argType === 'integer'}"
                    step="${argType === 'integer' ? 1 : 'any'}"
                    @input=${(e) => this._updateInputValueTyped(toolName, arg.name, e.target.value, argType)}
                    @blur=${(e) => this._updateInputValueTyped(toolName, arg.name, e.target.value, argType)}>
                </vaadin-number-field>
            `;
        } else if (argType === 'array' || argType === 'object') {
            return html`
                <vaadin-text-area
                    label="${arg.name} (JSON)"
                    helper-text="${arg.description || ''}"
                    placeholder="${arg.type || ''}"
                    ?required="${arg.required}"
                    @input=${(e) => this._updateInputValueTyped(toolName, arg.name, e.target.value, argType)}
                    @blur=${(e) => this._updateInputValueTyped(toolName, arg.name, e.target.value, argType)}>
                </vaadin-text-area>
            `;
        } else {
            return html`
                <vaadin-text-field
                    label="${arg.name}"
                    helper-text="${arg.description || ''}"
                    placeholder="${arg.type || ''}"
                    ?required="${arg.required}"
                    @input=${(e) => this._updateInputValueTyped(toolName, arg.name, e.target.value, 'string')}
                    @blur=${(e) => this._updateInputValueTyped(toolName, arg.name, e.target.value, 'string')}>
                </vaadin-text-field>
            `;
        }
    }

    _renderName(tool) {
        return html`<code>${tool.name}</code>`;
    }

    _renderClientName(tool) {
        return html`${tool.clientName}`;
    }

    _renderArgsCount(tool) {
        const argCount = tool.args ? tool.args.length : 0;
        if (argCount > 0) {
            const argNames = tool.args.map(a => a.name).join(', ');
            return html`<qui-badge level="contrast" pill small title="${argNames}"><span>${argCount}</span></qui-badge>`;
        }
        return html`<qui-badge level="default" pill small><span>0</span></qui-badge>`;
    }

    _updateInputValueTyped(toolName, argName, value, type) {
        const toolInputs = this._inputValues.get(toolName) || new Map();
        toolInputs.set(argName, { value: value, type: type });
        this._inputValues.set(toolName, toolInputs);
    }

    _applyFilter() {
        if (!this._allTools) {
            this._filtered = [];
            return;
        }
        if (!this._searchTerm) {
            this._filtered = [...this._allTools];
            return;
        }
        this._filtered = this._allTools.filter(tool => {
            const name = (tool.name || '').toLowerCase();
            const desc = (tool.description || '').toLowerCase();
            const clientName = (tool.clientName || '').toLowerCase();
            return name.includes(this._searchTerm) ||
                   desc.includes(this._searchTerm) ||
                   clientName.includes(this._searchTerm);
        });
    }

    _executeTool() {
        if (!this._selectedTool) return;
        const tool = this._selectedTool;
        const clientName = tool.clientName;
        const toolName = tool.name;

        // Build arguments JSON from form inputs
        const argsObj = {};
        const toolInputs = this._inputValues.get(toolName);
        if (toolInputs) {
            for (const [key, entry] of toolInputs.entries()) {
                argsObj[key] = this._convertValue(entry.value, entry.type);
            }
        }
        const argsJson = JSON.stringify(argsObj);

        this.jsonRpc.executeTool({ clientName: clientName, toolName: toolName, arguments: argsJson })
            .then(jsonRpcResponse => {
                this._setToolResult(jsonRpcResponse.result);
                this._showInputDialog = false;
                this._showResultDialog = true;
            })
            .catch(error => {
                this._setToolResult(`Error: ${JSON.stringify(error.error, null, 2)}`);
                this._showInputDialog = false;
                this._showResultDialog = true;
            });
    }

    _convertValue(value, type) {
        if (type === 'boolean') {
            return Boolean(value);
        } else if (type === 'integer') {
            const parsed = parseInt(value, 10);
            return isNaN(parsed) ? 0 : parsed;
        } else if (type === 'number') {
            const parsed = parseFloat(value);
            return isNaN(parsed) ? 0 : parsed;
        } else if (type === 'array' || type === 'object') {
            try {
                return JSON.parse(value);
            } catch (e) {
                return value;
            }
        }
        return value;
    }

    _setToolResult(result) {
        if (this._isJsonSerializable(result)) {
            this._toolResult = JSON.stringify(result, null, 2);
        } else {
            this._toolResult = result;
        }
    }

    _loadClients() {
        this.jsonRpc.clientInfos()
            .then(jsonRpcResponse => {
                this._clientInfos = jsonRpcResponse.result;
                // Flatten all tools from all clients into a single list
                this._allTools = [];
                for (const client of this._clientInfos) {
                    for (const tool of client.tools) {
                        this._allTools.push({
                            ...tool,
                            clientName: client.cdiName
                        });
                    }
                }
                this._applyFilter();
            });
    }

    _isJsonSerializable(obj) {
        try {
            JSON.stringify(obj);
            return true;
        } catch (e) {
            return false;
        }
    }
}

customElements.define('qwc-mcp-clients', QwcMcpClients);
