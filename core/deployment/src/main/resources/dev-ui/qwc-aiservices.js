import { LitElement, html, css} from 'lit';
import '@vaadin/grid';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid/vaadin-grid-sort-column.js';

import {aiservices} from 'build-time-data';


export class QwcAiservices extends LitElement {

    static styles = css`
        :host {
            height: 100%;
            display: flex;
        }
        vaadin-grid {
            margin-left: 15px;
            margin-right: 15px;
            height: 100%;
        }
    `;

    static properties = {
        "_aiservices": {state: true},
    }

    constructor() { 
        super();
        this._aiservices = aiservices;
    }

    render() {
        if (this._aiservices) {
            return this._renderAiServiceTable();
        } else {
            return html`<span>No AI services found</span>`;
        }
    }

    _renderAiServiceTable() {
        return html`
                <vaadin-grid .items="${this._aiservices}" theme="no-border">
                    <vaadin-grid-sort-column auto-width
                                        path="clazz"
                                        header="Name"
                                        ${columnBodyRenderer(this._nameRenderer, [])}>
                    </vaadin-grid-sort-column>
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