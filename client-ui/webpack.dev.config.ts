import path from "path";
import { Configuration, HotModuleReplacementPlugin } from "webpack";
import ForkTsCheckerWebpackPlugin from 'fork-ts-checker-webpack-plugin';
import HtmlWebpackPlugin from "html-webpack-plugin";
import ESLintPlugin from "eslint-webpack-plugin";

const config: Configuration = {
    mode: "development",
    output: {
        publicPath: "/",
    },
    entry: "./src/index.tsx",
    module: {
        rules: [
            {
                test: /\.(ts|js)x?$/i,
                exclude: /node_modules/,
                use: {
                    loader: "babel-loader",
                    options: {
                        presets: [
                            "@babel/preset-env",
                            "@babel/preset-react",
                            "@babel/preset-typescript",
                        ],
                    },
                },
            },
        ],
    },
    resolve: {
        extensions: [".tsx", ".ts", ".js"],
    },
    plugins: [
        new HtmlWebpackPlugin({
            template: "src/index.html",
        }),
        new ESLintPlugin({
            extensions: ["js", "jsx", "ts", "tsx"],
        }),
        new HotModuleReplacementPlugin(),
        new ForkTsCheckerWebpackPlugin({
            async: false
        }),

    ],
    devtool: "inline-source-map",
    devServer: {
        static: path.join(__dirname, "build"),
        historyApiFallback: true,
        port: 3000,
        open: true,
        hot: true,
        proxy:
            {
                '/api': 'http://localhost:9000',
            }
    },
};

export default config;
