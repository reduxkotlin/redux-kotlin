// composeApp/webpack.config.d/sqljs.config.js
const CopyWebpackPlugin = require("copy-webpack-plugin");
config.plugins.push(new CopyWebpackPlugin({
  patterns: [{ from: "../../node_modules/sql.js/dist/sql-wasm.wasm", to: "." }]
}));
config.resolve = config.resolve || {}; config.resolve.fallback = { fs: false, path: false, crypto: false };
