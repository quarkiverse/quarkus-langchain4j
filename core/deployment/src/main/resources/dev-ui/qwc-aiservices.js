import { LitElement, html, css} from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/icon';
import '@vaadin/button';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/form-layout';
import '@vaadin/progress-bar';
import '@vaadin/checkbox';
import '@vaadin/grid';
import 'qui-alert';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid/vaadin-grid-sort-column.js';

import {aiservices} from 'build-time-data';


export class QwcAiservices extends LitElement {

    static styles = css`
        .button {
            cursor: pointer;
        }
        .clearIcon {
            color: orange;
        }
        .message {
          padding: 15px;
          text-align: center;
          margin-left: 20%;
          margin-right: 20%;
          border: 2px solid orange;
          border-radius: 10px;
          font-size: large;
        }
        `;

    static properties = {
        "_aiservices": {state: true},
        "_message": {state: true}
    }

    connectedCallback() {
        super.connectedCallback();
        this._aiservices = aiservices;
    }

    render() {
        if (this._aiservices) {
            return this._renderAiServiceTable();
        } else {
            return html`<span>Loading AI services...</span>`;
        }
    }

    _renderAiServiceTable() {
        return html`
                ${this._message}
                <vaadin-grid .items="${this._aiservices}" class="datatable" theme="no-border">
                    <vaadin-grid-column auto-width
                                        header="Name"
                                        ${columnBodyRenderer(this._nameRenderer, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column auto-width
                                        header="Tools"
                                        ${columnBodyRenderer(this._toolsRenderer, [])}>
                    </vaadin-grid-column>
                </vaadin-grid>`;
    }

    _nameRenderer(aiservice) {
        return html`${aiservice.clazz}`;
    }

    _toolsRenderer(aiservice) {
        if (aiservice.tools && aiservice.tools.length > 0) {
            return html`<vaadin-vertical-layout>
                          ${aiservice.tools.map(tool =>
                html`<div><code>${tool}</code></div>`
            )}</vaadin-vertical-layout>`;
        }
    }

}
customElements.define('qwc-aiservices', QwcAiservices);