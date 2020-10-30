import {store} from 'kotlin-library'

Object.defineProperty(store, 'dispatch', {
    configurable: true,
    get: () => store._dispatch
})

Object.defineProperty(store, 'subscribe', {
    configurable: true,
    get: () => store._subscribe
})

Object.defineProperty(store, 'state', {
    configurable: true,
    get: store._getState
})

export {store}