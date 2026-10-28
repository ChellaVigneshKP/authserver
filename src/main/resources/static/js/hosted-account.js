/* Additive local account forms use only the issuer session, CSRF and one-use nonce. */
(() => {
    document.querySelectorAll('[data-hosted-continue]').forEach(link => {
        link.addEventListener('click', () => {
            const target = new URL(link.href);
            target.searchParams.set('x-request-datetime', new Date().toISOString());
            link.href = target.href;
        });
    });
    document.querySelectorAll('form[data-hosted-account]').forEach(form => {
        form.addEventListener('submit', () => {
            form.elements.namedItem('x-request-datetime').value = new Date().toISOString();
            form.querySelectorAll('input[type="password"]').forEach(field => {
                const bytes = new TextEncoder().encode(field.value);
                field.value = btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join(''));
            });
            form.querySelector('button[type="submit"]').disabled = true;
        });
    });
})();
