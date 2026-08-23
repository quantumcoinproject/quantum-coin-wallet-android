// Node harness: loads the shipping quantumcoin-bundle.js plus the inline
// bridge.html script blocks and exercises the wallet bridge end-to-end,
// fully offline (WASM init + key derivation run in-process; no network).
//
// Runs in the CURRENT realm (indirect eval into the global scope), not a
// vm sandbox: the SDK's WASM signer passes typed arrays across the JS/WASM
// boundary, and Node vm contexts have their own TypedArray realms which
// fail the SDK's internal instanceof checks - a false negative a real
// (single-realm) WebView can never hit.
//
// Run manually with:
//   node bridge-smoke-test.js [path-to-bridge.html]
'use strict';
const fs = require('fs');
const path = require('path');

const bridgeHtmlPath = process.argv[2] ||
    path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'bridge.html');
const bundlePath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'quantumcoin-bundle.js');

const results = [];
const pendingPayloads = new Map();

// Browser-ish globals the bundle + bridge expect.
global.window = global;
global.atob = (b64) => Buffer.from(b64, 'base64').toString('binary');
global.btoa = (bin) => Buffer.from(bin, 'binary').toString('base64');
global.AndroidBridge = {
    isDebug: () => false,
    onResult: (requestId, json) => results.push({ requestId, ...JSON.parse(json) }),
    getPendingPayload: (requestId) => {
        const v = pendingPayloads.get(requestId) || null;
        pendingPayloads.delete(requestId);
        return v;
    },
};

// Indirect eval -> global scope, so the bridge's top-level `var`s
// (bridge, hexToBytes, ...) become globals we can reach.
const globalEval = (0, eval);
globalEval(fs.readFileSync(bundlePath, 'utf8'));
const html = fs.readFileSync(bridgeHtmlPath, 'utf8');
const re = /<script>([\s\S]*?)<\/script>/g;
let m;
while ((m = re.exec(html))) {
    globalEval(m[1]);
}

let failures = 0;
function check(name, cond, detail) {
    if (cond) { console.log('PASS', name); }
    else { failures++; console.error('FAIL', name, detail || ''); }
}
function envelope(requestId) {
    return results.find((r) => r.requestId === requestId);
}

(async () => {
    check('QuantumCoinSDK defined', typeof global.QuantumCoinSDK !== 'undefined');
    check('SeedWordsSDK defined', typeof global.SeedWordsSDK !== 'undefined');
    check('bridge object defined', typeof global.bridge === 'object');
    for (const fn of ['initialize', 'initializeOffline', 'createRandomSeed', 'createRandom',
        'walletFromSeed', 'walletFromPhrase', 'walletFromKeys', 'sendTransaction',
        'sendTokenTransaction', 'isValidAddress', 'getChecksumAddress', 'computeAddress',
        'encryptWalletJson', 'decryptWalletJson', 'getAllSeedWords', 'doesSeedWordExist',
        'scryptDerive', 'estimateGas']) {
        check('bridge.' + fn, typeof global.bridge[fn] === 'function');
    }

    // Offline SDK bring-up (WASM decompress + integrity check + go.run).
    await global.bridge.initializeOffline('r0');
    const r0 = envelope('r0');
    check('initializeOffline succeeds', r0 && r0.success === true, JSON.stringify(r0));

    // Wallet creation: address + base64 keys + seed words in the envelope
    // (this repo's bridge predates the iOS binary channel, so the keys
    // legitimately travel base64 inside the JSON result).
    await global.bridge.createRandom('r1', 0);
    const r1 = envelope('r1');
    check('createRandom succeeds', r1 && r1.success === true, JSON.stringify(r1 && r1.error));
    const d1 = (r1 && r1.data) || {};
    check('createRandom address shape', /^0x[0-9a-fA-F]{64}$/.test(String(d1.address || '')), d1.address);
    check('createRandom returns keys',
        typeof d1.privateKey === 'string' && d1.privateKey.length > 0 &&
        typeof d1.publicKey === 'string' && d1.publicKey.length > 0);
    check('createRandom seed words', Array.isArray(d1.seedWords) && d1.seedWords.length > 0,
        JSON.stringify(d1.seedWords ? d1.seedWords.length : null));
    check('createRandom seed hex', typeof d1.seed === 'string' && d1.seed.length > 0);

    // Determinism round-trip: rebuilding the wallet from the same seed
    // must produce the same address (staged-payload pull path).
    const seedBytes = Array.from(global.hexToBytes(d1.seed));
    pendingPayloads.set('r2', JSON.stringify({ seedArray: seedBytes }));
    await global.bridge.walletFromSeed('r2');
    const r2 = envelope('r2');
    check('walletFromSeed succeeds', r2 && r2.success === true, JSON.stringify(r2 && r2.error));
    check('walletFromSeed address matches createRandom',
        r2 && r2.data && r2.data.address === d1.address,
        r2 && r2.data && r2.data.address);

    // Seed-word module boundary: lookups work through the separate global.
    const firstWord = d1.seedWords && d1.seedWords[0];
    check('doesSeedWordExist(firstWord)',
        firstWord && global.SeedWordsSDK.doesSeedWordExist(String(firstWord).trim()) === true,
        String(firstWord));

    console.log(failures === 0 ? 'ALL PASS' : failures + ' FAILURES');
    process.exit(failures === 0 ? 0 : 1);
})().catch((e) => { console.error('HARNESS ERROR', e); process.exit(1); });
