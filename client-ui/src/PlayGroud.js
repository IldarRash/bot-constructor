import React, {useContext, useEffect, useRef, useState} from 'react';
import {encodeCompositeMetadata, encodeRoute, MESSAGE_RSOCKET_ROUTING} from "rsocket-core";
import {Flowable} from "rsocket-flowable/build";
import RSocketContext from "./context/RSocketContext";

const PlayGround = () => {
  const [template, setTemplate] = useState({name: "test"});
  const [edges, setEdges] = useState([]);
  const [answers, setAnswers] = useState([]);
  const [status, rSocket] = useContext(RSocketContext)
  const [sink, setSink] = useState()
  const [source, setSource] = useState()
  const answerCount = useRef(0)

  useEffect( async () => {
    console.log("Playground =>", rSocket)
    let flowConnect  = () =>  {
      const flow = new Flowable(subscriber => {
        subscriber.onSubscribe({
          cancel: () => undefined,
          request: (n) => {
            console.log("N", n)
            setSink(subscriber);
          }
        })
      });


      rSocket.requestChannel(flow)
          .subscribe({
            onSubscribe: sub => {
              console.log("Sub form client", sub)
              sub.request(0x7fffffff)
              setSource(sub)
            },
            onNext: ({data}) => {
              let answer = data.toString()
              addAnswer(answer)
            },
            onError: console.error,
            onComplete: msg => {
              console.log('Msg', msg)
            },
          })
    }

    await flowConnect()
  }, []);

  const addEdge = ( userInput ) => {
    let copy = [...edges];
    let edge = { id: edges.length + 1, target: userInput, source: userInput, type: "edge" }

    sink.onNext({
      metadata: encodeCompositeMetadata([
        [MESSAGE_RSOCKET_ROUTING, encodeRoute(`bot.template.${template.id}.edit`)],
      ]),
      data: Buffer.from(JSON.stringify(edge))
    })
    copy = [...copy, edge];
    setEdges(copy);
  }

  const addAnswer = ( answer ) => {
    let ans = { id: answerCount.current++, target: answer }
    let copy = [...answers];
    console.log("Before add", copy)
    let newCopy = copy.concat(ans)
    console.log("After add", )
    setAnswers(newCopy);
  }

  function createTemplate() {
    console.log("This client", rSocket)
    rSocket
      .requestResponse({
          metadata: encodeCompositeMetadata([
            [MESSAGE_RSOCKET_ROUTING, encodeRoute('bot.template.new')],
          ]),
          data: Buffer.from(JSON.stringify(template.name))
        }
      ).subscribe({
      onComplete({ data }) {
        let json = JSON.parse(data.toString())
        console.log('onComplete', json);
        setTemplate(json)
      }
    });
  }

  function cansel() {
    sink.cancel()
  }

  return (
    <div>
      <input
        type='search'
        value={template.name}
        onChange={event => setTemplate({name: event.target.value})}
      />
      <div>
        <div>
          <h3>{template.id}</h3>
          <h3> {template.name} </h3>
          <h3> {template.ownerId} </h3>
        </div>
        <button onClick={createTemplate}>Create new template</button>
      </div>
      <EdgeForm addEdge={addEdge}/>
      {edges.length > 0 ? (
        <div>
          <div>
            <span>Список элементов на вход</span>
            <EdgeList edgeList={edges}/>
          </div>
          <div>
            <span>Список элементов c сервера</span>
            <EdgeList edgeList={answers}/>
          </div>
        </div>
        ) : (
        <p> No edges</p>
        )}
      <button onClick={cansel}>Cansel channel</button>
    </div>

  );
};

const EdgeList = ({edgeList}) => {
  return (
    <div>
      {edgeList.map(edge => {
        return (
          <li key={edge.id}>
            <Edge edge={edge} />
          </li>
        )
      })}
    </div>
  );
};

const Edge = ({edge}) => {
  return (
    <div>
      <span> {edge.id} </span>
      <span> {edge.target} </span>
      <span> {edge.source} </span>
    </div>
  );
};


const EdgeForm = ({ addEdge }) => {

  const [ userInput, setUserInput ] = useState('');

  const handleChange = (e) => {
    setUserInput(e.currentTarget.value)
  }

  const handleSubmit = (e) => {
    e.preventDefault();
    addEdge(userInput);
    setUserInput("");
  }
  return (
    <form onSubmit={handleSubmit}>
      <input value={userInput} type="text" onChange={handleChange} placeholder="Enter task..."/>
      <button>Submit</button>
    </form>
  );
};

export default PlayGround;

