const quantumcoin = require('quantumcoin');
const quantumswap = require('quantumswap');
const seedWords = require('seed-words');

module.exports = {
  ...quantumcoin,
  ...quantumswap,
  getWordListFromSeedArray: seedWords.getWordListFromSeedArray,
  getSeedArrayFromWordList: seedWords.getSeedArrayFromWordList,
};
