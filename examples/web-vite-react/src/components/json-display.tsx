import JsonView, {ReactJsonViewProps} from "@microlink/react-json-view";

export const JsonDisplay = ({src, collapsed = 1}: Pick<ReactJsonViewProps, 'src' | 'collapsed'>) => (
    <JsonView
        src={src}
        style={{background: 'none'}}
        theme="bright"
        collapsed={collapsed}
        displayDataTypes={false}
        enableClipboard={false}
    />
);