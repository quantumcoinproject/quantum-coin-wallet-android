var cryptoBrowserify = require('crypto-browserify');
var scryptJs = require('scrypt-js');

var cryptoShim = Object.create(cryptoBrowserify);

cryptoShim.scryptSync = function(password, salt, keylen, options) {
    var N = (options && options.N) || 16384;
    var r = (options && options.r) || 8;
    var p = (options && options.p) || 1;

    var passwordBuf = (password instanceof Uint8Array) ? password : Buffer.from(password);
    var saltBuf = (salt instanceof Uint8Array) ? salt : Buffer.from(salt);

    return Buffer.from(scryptJs.syncScrypt(passwordBuf, saltBuf, N, r, p, keylen));
};

module.exports = cryptoShim;
