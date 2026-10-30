import {css, html, LitElement} from 'lit';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';
import {columnBodyRenderer} from '@vaadin/grid/lit.js';

import {guardrails} from 'build-time-data';

/**
 * Lists the guardrails registered in the application, organized by guardrail class: its kind
 * (input/output, plus the tool variants), and every AI service or tool method applying it, with
 * its position in the chain and, for output guardrails, the configured max retries.
 */
export class QwcGuardrails extends LitElement {

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

        .kind {
            display: inline-block;
            padding: 1px 8px;
            border-radius: var(--lumo-border-radius-s);
            font-size: var(--lumo-font-size-xs);
            font-weight: 600;
            color: var(--lumo-base-color);
        }

        .kind.input {
            background-color: var(--lumo-primary-color);
        }

        .kind.output {
            background-color: var(--lumo-success-color);
        }

        .usage {
            padding: 2px 0;
            overflow-wrap: anywhere;
        }

        .scope {
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        "_guardrails": {state: true},
    }

    constructor() {
        super();
        this._guardrails = guardrails;
    }

    render() {
        if (this._guardrails && this._guardrails.length > 0) {
            return this._renderGuardrailsTable();
        } else {
            return html`<span>No guardrails found</span>`;
        }
    }

    _renderGuardrailsTable() {
        return html`
            <vaadin-grid .items="${this._guardrails}" theme="no-border row-stripes wrap-cell-content">
                <vaadin-grid-sort-column auto-width
                                         flex-grow="0"
                                         path="className"
                                         header="Guardrail">
                </vaadin-grid-sort-column>
                <vaadin-grid-sort-column auto-width
                                         flex-grow="0"
                                         path="kind"
                                         header="Type"
                                         ${columnBodyRenderer(this._kindRenderer, [])}>
                </vaadin-grid-sort-column>
                <vaadin-grid-column path="usedBy"
                                    header="Used by"
                                    ${columnBodyRenderer(this._usedByRenderer, [])}>
                </vaadin-grid-column>
            </vaadin-grid>`;
    }

    _kindRenderer(guardrail) {
        const direction = guardrail.kind.toLowerCase().includes('output') ? 'output' : 'input';
        return html`<span class="kind ${direction}">${guardrail.kind}</span>`;
    }

    _usedByRenderer(guardrail) {
        return html`${guardrail.usedBy.map((usage) => html`
            <div class="usage">
                <code>${usage.owner}</code>
                <span class="scope">
                    &mdash; ${this._scope(usage)} &middot; #${usage.position}${usage.maxRetries != null ? html`, max retries: ${usage.maxRetries}` : ''}
                </span>
            </div>`)}`;
    }

    _scope(usage) {
        if (usage.method != null) {
            return html`${usage.method}()`;
        }
        if (usage.excludedMethods && usage.excludedMethods.length > 0) {
            return html`all methods except ${usage.excludedMethods.map((m) => m + '()').join(', ')}`;
        }
        return 'all methods';
    }

}

customElements.define('qwc-guardrails', QwcGuardrails);
