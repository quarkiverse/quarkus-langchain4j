import {LitElement, html, css} from 'lit';
import '@vaadin/text-area';
import '@vaadin/button';
import '@vaadin/checkbox';
import '@vaadin/details';
import '@vaadin/vertical-layout';
import '@vaadin/message-input';
import '@vaadin/message-list';
import '@vaadin/progress-bar';
import '@vaadin/text-field';
import '@vaadin/icon';
import '@vaadin/icons';
import 'qui-alert';
import { JsonRpc } from 'jsonrpc';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';

export class QwcMcpClients extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            height: 100%;
            display: flex;
            flex-direction: column;
            margin-left: 15px;
            margin-right: 15px;
        }
    `;

    static properties = {
        _clientInfos: {state: true},
    }

    constructor() {
        super();
        this._clientInfos = [];
        this.jsonRpc.clientInfos().then(jsonRpcResponse => {
            this._clientInfos = jsonRpcResponse.result;
        });
    }

    render() {
        if(this._clientInfos === []) {
            return html`Loading...`;
         } else return html`
            ${this._clientInfos.map(clientInfo => html`
                
              <h2>MCP client: ${clientInfo.cdiName}</h2>  
              <h3>Tools</h3>
              <vaadin-grid .items="${clientInfo.tools}" theme="wrap-cell-content">
                  <vaadin-grid-sort-column resizable
                                           path="name"
                                           header="Name">
                  </vaadin-grid-sort-column>
                  <vaadin-grid-sort-column resizable
                                           path="description"
                                           header="Description">
                  </vaadin-grid-sort-column>
                  <vaadin-grid-sort-column resizable
                                           header="Execute"
                                           ${columnBodyRenderer((tool => this._toolExecuteColumnRenderer(tool, clientInfo)), [])}>
                  </vaadin-grid-sort-column>
              </vaadin-grid>
            `)}            
        `;
    }

    _toolExecuteColumnRenderer(tool, clientInfo) {
        var actualArguments = tool.exampleInput;
        const textAreaId = `output-${clientInfo.cdiName}-${tool.name}`
        return html`
            <vaadin-vertical-layout>
                <vaadin-text-area label="Arguments" value=${tool.exampleInput} style="width: 100%;" 
                                   @change=${(e) => actualArguments = e.detail.sourceEvent.currentTarget.value}></vaadin-text-area>
                <vaadin-button @click=${() => {
                    this.jsonRpc.executeTool({clientName: clientInfo.cdiName, toolName: tool.name, arguments: actualArguments})
                                .then(jsonRpcResponse => {
                                    let outputElement = this.shadowRoot.getElementById(textAreaId);
                                    outputElement.style = "width: 100%; display: block;";
                                    outputElement.value = JSON.stringify(jsonRpcResponse.result);
                                });}
                }>Execute</vaadin-button>
                <vaadin-text-area label="Output" id=${textAreaId} disabled style="width: 100%; display: none;"></vaadin-text-area>
            </vaadin-vertical-layout>
        `
    }

}

customElements.define('qwc-mcp-clients', QwcMcpClients);
