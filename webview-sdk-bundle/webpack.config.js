const path = require('path');
const webpack = require('webpack');

module.exports = {
  entry: './src/index.js',
  output: {
    filename: 'quantumcoin-bundle.js',
    path: path.resolve(__dirname, '..', 'app', 'src', 'main', 'assets'),
    library: {
      name: 'QuantumCoinSDK',
      type: 'var',
    },
  },
  target: 'web',
  resolve: {
    fallback: {
      crypto: path.resolve(__dirname, 'src/crypto-shim.js'),
      stream: require.resolve('stream-browserify'),
      buffer: require.resolve('buffer/'),
      events: require.resolve('events/'),
      util: false,
      fs: false,
      path: require.resolve('path-browserify'),
      os: require.resolve('os-browserify/browser'),
      vm: require.resolve('vm-browserify'),
      assert: require.resolve('assert/'),
      http: require.resolve('http-browserify'),
      https: require.resolve('https-browserify'),
      url: require.resolve('url/'),
      net: false,
      tls: false,
      child_process: false,
      dns: false,
      readline: false,
      string_decoder: require.resolve('string_decoder/'),
      zlib: false,
      'node:net': false,
      'node:crypto': path.resolve(__dirname, 'src/crypto-shim.js'),
    },
    alias: {
      process: 'process/browser',
      util: path.resolve(__dirname, 'src/util-shim.js'),
      quantumcoin: path.resolve(__dirname, 'node_modules/quantumcoin'),
    },
    symlinks: true,
  },
  plugins: [
    new webpack.NormalModuleReplacementPlugin(/^node:/, (resource) => {
      resource.request = resource.request.replace(/^node:/, '');
    }),
    new webpack.ProvidePlugin({
      Buffer: ['buffer', 'Buffer'],
      process: 'process/browser',
    }),
    new webpack.DefinePlugin({
      'process.env.NODE_DEBUG': JSON.stringify(''),
    }),
  ],
  module: {
    rules: [
      {
        test: /\.wasm$/,
        type: 'asset/inline',
      },
    ],
  },
  performance: {
    maxAssetSize: 10 * 1024 * 1024,
    maxEntrypointSize: 10 * 1024 * 1024,
  },
};
