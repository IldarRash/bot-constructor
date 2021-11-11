import React from 'react';

export default [
    {
        id: '1',
        type: 'input',
        data: {label: 'An input node'},
        position: {x: 0, y: 50},
        sourcePosition: 'right',
    },
    {
        id: '2',
        type: 'selectorNode',
        data: {onChange: onChange, color: initBgColor},
        style: {border: '1px solid #777', padding: 10},
        position: {x: 300, y: 50},
    },
    {
        id: '3',
        type: 'output',
        data: {label: 'Output A'},
        position: {x: 650, y: 25},
        targetPosition: 'left',
    },
    {
        id: '4',
        type: 'output',
        data: {label: 'Output B'},
        position: {x: 650, y: 100},
        targetPosition: 'left',
    },
   /* {
        id: '5',
        data: {
            label: 'Node id: 5',
        },
        position: {x: 250, y: 325},
    },
    {
        id: '6',
        type: 'output',
        data: {
            label: (
                <>
                    An <strong>output node</strong>
                </>
            ),
        },
        position: {x: 100, y: 480},
    },
    {
        id: '7',
        type: 'output',
        data: {label: 'Another output node'},
        position: {x: 400, y: 450},
    },
    {id: 'e1-2', source: '1', target: '2', label: 'this is an edge label'},
    {id: 'e1-3', source: '1', target: '3'},
    {
        id: 'e3-4',
        source: '3',
        target: '4',
        animated: true,
        label: 'animated edge',
    },
    {
        id: 'e4-5',
        source: '4',
        target: '5',
        arrowHeadType: 'arrowclosed',
        label: 'edge with arrow head',
    },
    {
        id: 'e5-6',
        source: '5',
        target: '6',
        type: 'smoothstep',
        label: 'smooth step edge',
    },
    {
        id: 'e5-7',
        source: '5',
        target: '7',
        type: 'step',
        style: {stroke: '#f6ab6c'},
        label: 'a step edge',
        animated: true,
        labelStyle: {fill: '#f6ab6c', fontWeight: 700},
    },*/
];
