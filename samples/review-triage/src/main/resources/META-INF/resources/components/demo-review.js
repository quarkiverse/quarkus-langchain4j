import {css, html, LitElement} from 'lit';
import '@vaadin/button';
import '@vaadin/text-area';
import '@vaadin/progress-bar';
import '@vaadin/horizontal-layout';

/**
 * Contains a field to enter the review, the submit button and the message from the backend.
 */
export class DemoReview extends LitElement {
    static styles = css`
      .button {
        cursor: pointer;
      }

      vaadin-text-area {
        width: 35em;
        height: 10rem;
      }

      vaadin-button {
        margin-left: 1em;
      }

      .outcome {
        margin-top: 1em;
        width: 100%;
      }
    `;

    static properties = {
        last_message: String,
        post: String,
        in_progress: {state: true, type: Boolean},
        complete: {state: true, type: Boolean}
    }

    constructor() {
        super();
        this.post = "";
        this.last_message = "";
        this.in_progress = false;
        this.complete = false;
    }

    render() {
        let outcome = "";
        if (this.in_progress) {
            outcome = html`
                <div class="outcome">
                    <div>Analyzing your post...</div>
                    <vaadin-progress-bar indeterminate theme="contrast"></vaadin-progress-bar>
                </div>`;
        } else if (this.complete) {
            outcome = html`
                <div class="outcome">
                    <div>${this.last_message}</div>
                </div>`;
        } else {
            outcome = html`
                <div class="outcome"></div>`;
        }

        return html`
            <vaadin-horizontal-layout theme="spacing padding"
                                      style="align-items: baseline">

            <vaadin-vertical-layout theme="spacing padding"
                                      style="align-items: baseline">

              ${outcome}
            </vaadin-vertical-layout>

            </vaadin-horizontal-layout>
            <vaadin-horizontal-layout theme="spacing padding"
                                      style="align-items: baseline">


                <vaadin-text-area
                        label="Add your post:"
                        .maxlength=1024
                        .helperText="${`${this.post.length}/1024`}"
                        .value=${this.post}
                        @value-changed="${event => {
                            this.post = event.detail.value;
                        }}"
                >
                </vaadin-text-area>
                <vaadin-button
                        arial-label="Submit your post"
                        @click=${this._submit} class="button primary">
                    Submit
                </vaadin-button>
            </vaadin-horizontal-layout>`;
    }

    _submit() {
        this.last_message = "";
        this.in_progress = true;
        this.complete = false;
        fetch(`/review`, {
            method: "POST",
            body: JSON.stringify({
                post: this.post
            }),
            headers: {
                "Content-Type": "application/json",
            },
        }).then(response => response.json())
            .then(data => {
                this.in_progress = false;
                this.complete = true;
                this.post = "";
                this.last_message = "Sarcasm detected: " + data.sarcasmDetected;
            })
            .catch((error) => {
                console.error('Error:', error);
            });
    }

}

customElements.define('demo-review', DemoReview);
