/* Browser forms preserve the server's Base64 password and signed DTO contracts. */
(() => {
    const utf8 = value => new TextEncoder().encode(value);
    const base64 = bytes => btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join(''));
    const encodePassword = value => base64(utf8(value));
    const fromBase64 = value => Uint8Array.from(atob(value), char => char.charCodeAt(0));
    const hidden = (form, name, value) => {
        let field = form.elements.namedItem(name);
        if (!field) {
            field = document.createElement('input');
            field.type = 'hidden';
            field.name = name;
            form.append(field);
        }
        field.value = value;
    };
    document.querySelectorAll('form[data-encode-password], form[data-signed-profile]').forEach(form => {
        form.addEventListener('submit', async event => {
            event.preventDefault();
            const button = form.querySelector('button[type="submit"]');
            if (button.disabled) return;
            const error = document.querySelector('[data-form-error]');
            button.disabled = true;
            error.hidden = true;
            try {
                const passwordFields = [...form.querySelectorAll('input[type="password"]')];
                const confirmation = form.elements.namedItem('confirmPassword');
                const original = form.elements.namedItem('newPassword') || form.elements.namedItem('password');
                if (confirmation && original && confirmation.value !== original.value) {
                    throw new Error('The passwords do not match.');
                }
                const encoded = new Map(passwordFields.map(field => [field.name, encodePassword(field.value)]));
                if (form.hasAttribute('data-signed-profile')) {
                    if (!window.crypto?.subtle) throw new Error('This browser requires HTTPS to sign the request.');
                    const response = await fetch(form.dataset.ssoUrl, {
                        credentials: 'same-origin', headers: {'x-request-datetime': new Date().toISOString()}
                    });
                    if (!response.ok) throw new Error('Your session has expired. Sign in again from your application.');
                    const token = await response.json();
                    if (!token.access_token || !token.signing_key) throw new Error('No active session token is available. Sign in again.');
                    const dto = {};
                    [...form.querySelectorAll('input:not([type="hidden"])')].forEach(field => {
                        dto[field.name] = encoded.get(field.name) ?? field.value;
                    });
                    // Jackson DTO declaration order matches the form's field order.
                    const keyBytes = Array.isArray(token.signing_key) ? new Uint8Array(token.signing_key) : fromBase64(token.signing_key);
                    const key = await crypto.subtle.importKey('raw', keyBytes, {name: 'HMAC', hash: 'SHA-256'}, false, ['sign']);
                    const signature = await crypto.subtle.sign('HMAC', key, utf8(JSON.stringify(dto)));
                    hidden(form, 'Authorization', 'Bearer ' + token.access_token);
                    hidden(form, 'x-signature', base64(new Uint8Array(signature)));
                    hidden(form, 'x-request-datetime', new Date().toISOString());
                    hidden(form, 'x-request-id', crypto.randomUUID());
                }
                encoded.forEach((value, name) => { form.elements.namedItem(name).value = value; });
                HTMLFormElement.prototype.submit.call(form);
            } catch (failure) {
                error.textContent = failure.message || 'Unable to submit this request. Please try again.';
                error.hidden = false;
                button.disabled = false;
            }
        });
    });
})();
