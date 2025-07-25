import React from 'react';
import { Button, Props as ButtonProps } from 'components/common/Button/Button';
import * as S from 'components/common/ActionComponent/ActionComponent.styled';
import {
  ActionComponentProps,
  getDefaultActionMessage,
} from 'components/common/ActionComponent/ActionComponent';
import { useActionTooltip } from 'lib/hooks/useActionTooltip';

interface Props extends Omit<ActionComponentProps, 'permission'>, ButtonProps {
  canDoAction: boolean;
}

const ActionButton: React.FC<Props> = ({
  placement = 'bottom-end',
  message = getDefaultActionMessage(),
  disabled,
  canDoAction,
  ...props
}) => {
  const isDisabled = !canDoAction;

  const { x, y, refs, strategy, open } = useActionTooltip(
    isDisabled || disabled,
    placement
  );

  return (
    <S.Wrapper ref={refs.setReference}>
      <Button {...props} disabled={disabled || isDisabled} />
      {open && (
        <S.MessageTooltipLimited
          ref={refs.setFloating}
          style={{
            position: strategy,
            top: y ?? 0,
            left: x ?? 0,
            width: 'max-content',
          }}
        >
          {message}
        </S.MessageTooltipLimited>
      )}
    </S.Wrapper>
  );
};

export default ActionButton;
