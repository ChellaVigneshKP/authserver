import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {webcrypto, createHmac} from 'node:crypto';

const script = readFileSync(new URL('../../main/resources/static/js/auth-forms.js', import.meta.url), 'utf8');
function browser(fields, {signed = false, token = {access_token: 'valid-token', signing_key: Buffer.from('signing-key').toString('base64')}, ok = true} = {}) {
    const inputs = fields.map(([name, value, type = 'text']) => ({name, value, type}));
    const button = {disabled: false};
    const error = {hidden: true, textContent: ''};
    let listener;
    let submitted = false;
    const form = {
        dataset: {ssoUrl: '/services/oauth2/sso'},
        elements: {namedItem: name => inputs.find(input => input.name === name)},
        addEventListener: (_, callback) => { listener = callback; },
        hasAttribute: attr => attr === 'data-signed-profile' && signed,
        querySelector: () => button,
        querySelectorAll: selector => inputs.filter(input => selector.includes('password') ? input.type === 'password' : input.type !== 'hidden'),
        append: input => inputs.push(input)
    };
    const dom = {
        querySelectorAll: () => [form],
        querySelector: () => error,
        createElement: () => ({})
    };
    function HTMLFormElement() {}
    HTMLFormElement.prototype.submit = () => { submitted = true; };
    runInNewContext(script, {
        document: dom, window: {crypto: webcrypto}, crypto: webcrypto, TextEncoder, Uint8Array,
        btoa, atob, HTMLFormElement,
        fetch: async (url, options) => {
            assert.equal(url, '/services/oauth2/sso');
            assert.equal(options.credentials, 'same-origin');
            assert.ok(!Number.isNaN(Date.parse(options.headers['x-request-datetime'])));
            return {ok, json: async () => token};
        }
    });
    return {submit: () => listener({preventDefault() {}}), value: name => form.elements.namedItem(name)?.value,
        submitted: () => submitted, error, button};
}

test('login submits UTF-8 Base64 password once', async () => {
    const page = browser([['username', 'alice'], ['password', 'Pässword£12', 'password']]);
    await page.submit();
    assert.equal(page.value('password'), Buffer.from('Pässword£12').toString('base64'));
    assert.equal(page.value('username'), 'alice');
    assert.equal(page.submitted(), true);
});
test('password confirmation mismatch does not submit or encode input', async () => {
    const page = browser([['password', 'one', 'password'], ['confirmPassword', 'two', 'password']]);
    await page.submit();
    assert.equal(page.submitted(), false);
    assert.equal(page.value('password'), 'one');
    assert.equal(page.error.hidden, false);
    assert.equal(page.button.disabled, false);
});
test('profile signs the exact UTF-8 DTO payload with session token key', async () => {
    const fields = [['firstName', 'Jöhn'], ['lastName', 'Smith'], ['email', 'a@example.com'], ['confirmEmail', 'a@example.com'], ['phoneNumber', '1234567890'], ['secondaryPhoneNumber', '']];
    const page = browser(fields, {signed: true});
    await page.submit();
    const expected = createHmac('sha256', 'signing-key').update(JSON.stringify(Object.fromEntries(fields))).digest('base64');
    assert.equal(page.value('x-signature'), expected);
    assert.equal(page.value('Authorization'), 'Bearer valid-token');
    assert.ok(!Number.isNaN(Date.parse(page.value('x-request-datetime'))));
    assert.equal(page.submitted(), true);
});
test('password change signs encoded DTO then posts those exact values', async () => {
    const fields = [['currentPassword', 'oldPassword!', 'password'], ['newPassword', 'NewPassword£123', 'password'], ['confirmPassword', 'NewPassword£123', 'password']];
    const page = browser(fields, {signed: true});
    await page.submit();
    const dto = Object.fromEntries(fields.map(([name, value]) => [name, Buffer.from(value).toString('base64')]));
    assert.equal(page.value('x-signature'), createHmac('sha256', 'signing-key').update(JSON.stringify(dto)).digest('base64'));
    for (const [name, value] of Object.entries(dto)) assert.equal(page.value(name), value);
    assert.equal(page.submitted(), true);
});
test('expired SSO session leaves original fields and displays controlled error', async () => {
    const page = browser([['currentPassword', 'old', 'password'], ['newPassword', 'new', 'password'], ['confirmPassword', 'new', 'password']], {signed: true, ok: false});
    await page.submit();
    assert.equal(page.submitted(), false);
    assert.equal(page.value('newPassword'), 'new');
    assert.match(page.error.textContent, /session has expired/);
});
