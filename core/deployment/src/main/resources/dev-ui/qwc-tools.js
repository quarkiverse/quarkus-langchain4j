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

import {tools} from 'build-time-data';


export class QwcTools extends LitElement {

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
        "_tools": {state: true},
    }

    connectedCallback() {
        super.connectedCallback();
        this._tools = tools;
    }

    render() {
        if (this._tools) {
            return this._renderToolTable();
        } else {
            return html`<span>Loading tools...</span>`;
        }
    }

    _renderToolTable() {
        return html`
                <vaadin-grid .items="${this._tools}" class="datatable" theme="no-border">
                    <vaadin-grid-column auto-width
                                        header="Class name"
                                        ${columnBodyRenderer(this._classNameRenderer, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column auto-width
                                        header="Tool name"
                                        ${columnBodyRenderer(this._nameRenderer, [])}>
                    </vaadin-grid-column>
                    <vaadin-grid-column auto-width
                                        header="Description"
                                        ${columnBodyRenderer(this._descriptionRenderer, [])}>
                    </vaadin-grid-column>
                </vaadin-grid>`;
    }

    _actionRenderer(tool) {
        return html`
            <vaadin-button theme="small" @click=${() => this._reset(ds)} class="button">
                <vaadin-icon class="clearIcon" icon="font-awesome-solid:broom"></vaadin-icon> Reset
            </vaadin-button>`;
    }

    _classNameRenderer(tool) {
        return html`${tool.className}`;
    }


    _nameRenderer(tool) {
        return html`${tool.name}`;
    }

    _descriptionRenderer(tool) {
        return html`${tool.description}`;
    }

}
customElements.define('qwc-tools', QwcTools);