import {css, html, LitElement} from 'lit';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';

import {toolProviders, tools} from 'build-time-data';


export class QwcTools extends LitElement {

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
        "_tools": {state: true},
        "_toolProviders": {state: true},
    }

    constructor() {
        super();
        this._tools = tools;
        this._toolProviders = toolProviders;
    }

    render() {
        if (this._toolProviders.length > 0) {
            return this._renderToolProvider();
        } else if (this._tools) {
            return this._renderToolTable();
        } else {
            return html`<span>No tools found</span>`;
        }
    }

    _renderToolProvider() {
        return html`
            <vaadin-grid .items="${this._toolProviders}" theme="no-border">
                <vaadin-grid-sort-column auto-width
                                         path="className"
                                         header="Class name">
                </vaadin-grid-sort-column>
                <vaadin-grid-column auto-width
                                    path="aiServiceName"
                                    header="AiService">
                </vaadin-grid-column>
            </vaadin-grid>`;
    }

    _renderToolTable() {
        return html`
            <vaadin-grid .items="${this._tools}" theme="no-border">
                <vaadin-grid-sort-column auto-width
                                         path="className"
                                         header="Class name">
                </vaadin-grid-sort-column>
                <vaadin-grid-column auto-width
                                    path="name"
                                    header="Tool name">
                </vaadin-grid-column>
                <vaadin-grid-column auto-width
                                    path="description"
                                    header="Description">
                </vaadin-grid-column>
            </vaadin-grid>`;
    }

}

customElements.define('qwc-tools', QwcTools);