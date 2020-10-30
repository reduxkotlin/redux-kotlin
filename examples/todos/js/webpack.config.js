const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = {
    mode: 'development',
    entry: path.resolve(__dirname, 'js/index.jsx'),
    output: {
        path: path.resolve(__dirname, './dist'),
        filename: 'build.js'
    },
    resolve: {
        alias: {
            "kotlin-library": path.resolve(__dirname, 'build/js/packages/kotlin-react-redux')
        },
        extensions: ['.js', '.jsx']
    },
    module: {
        rules: [
            {
                test: /\.(js|jsx)$/,
                exclude: /node_modules/,
                use: {
                    loader: "babel-loader"
                }
            }
        ]
    },
    plugins: [new HtmlWebpackPlugin({
        template: 'index.html'
    })]
}